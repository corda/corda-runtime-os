package net.corda.crypto.merkle.impl

import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTree

/**
 *  leaves:         [L0, L1, L2, L3, L4]
 *                    |   |   |   |   |
 *  leafHashes:     [H0, H1, H2, H3, H4]
 *
 *  nodeHashes:    [[H0, H1, H2, H3, H4]
 *                     \ /     \ /    |
 *                  [ H01  ,  H23  , H4]
 *                      \    /       |
 *                  [    H03       , H4]
 *                          \      /
 *                  [         H04      ]
 *                  ]
 *                             |
 *  root:                     H04
 *
 *  Proof calculation example for one leaf:
 *  createAuditProof([2])
 *  To generate a proof for a set of indices we need to collect the hashes needed to recalculate the paths
 *  from the subject leaves towards the root.
 *  The elements on this route are enclosed by {} on the left:
 *
 *                                      inPath          level       outputHashes
 *                              init:
 *    H0  H1 {H2}  H3  H4               [0, 0, 1, 0, 0] 0           []
 *      \ /     \ /    |        1st iter:
 *     H01     {H23}   H4               [0, 1, 0]       1           [H3 ]
 *       \    /        |        2nd iter:
 *        {H03}        H4               [1, 0]          2           [H3, H01]
 *           \       /          3rd iter:
 *             {H04}                    [1]             3           [H3, H01, H4]
 *
 *
 * The right section after the tree shows the proof calculation's steps.
 *
 *  The proof:
 *  (
 *  5 (leaves.size),
 *  [IndexedMerkleLeaf(2, leafNonce(2), L2)]
 *  [H3, H01, H4]
 *  )
 *
 *  Proof calculation example for two leaves:
 *  createAuditProof([1,2])
 *                                      inPath          level       outputHashes
 *                              init:
 *    H0  {H1}{H2} H3  H4               [0, 1, 1, 0, 0] 0           []
 *      \ /     \ /    |        1st iter:
 *     {H01}   {H23}   H4               [1, 1, 0]       1           [H0, H3 ]
 *       \    /        |        2nd iter:
 *        {H03}        H4               [1, 0]          2           [H0, H3]
 *           \       /          3rd iter:
 *             {H04}                    [1]             3           [H0, H3, H4]
 *
 *  The proof:
 *  (
 *  5 (leaves.size),
 *  [IndexedMerkleLeaf(1, leafNonce(1), L1), IndexedMerkleLeaf(2, leafNonce(2), L2)]
 *  [H0, H3, H4]
 *  )
 *
 */

class MerkleTreeImpl(
    private val leaves: List<ByteArray>,
    private val digest: MerkleTreeHashDigestProvider
) : MerkleTree {

    init {
        require(leaves.isNotEmpty()) { "Merkle tree must have at least one item" }
    }

    companion object {
        @JvmStatic
        fun createMerkleTree(
            leaves: List<ByteArray>,
            digestProvider: MerkleTreeHashDigestProvider
        ): MerkleTreeImpl = MerkleTreeImpl(leaves, digestProvider)

        fun nextHigherPower2(value: Int): Int {
            require(value > 0) { "nextHigherPower2 requires positive value" }
            require(value <= 0x40000000) { "nextHigherPower2 requires smaller value" }
            // Fill (v-1)'s binary digits with ones after the highest position one.
            var v = value - 1
            v = v or (v ushr 1)
            v = v or (v ushr 2)
            v = v or (v ushr 4)
            v = v or (v ushr 8)
            v = v or (v ushr 16)
            return v + 1
        }

        fun treeDepth(size: Int): Int {
            val zeros = nextHigherPower2(size).countLeadingZeroBits()
            return 31 - zeros
        }

    }

    private val leafHashes: List<SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        leaves.mapIndexed { index, bytes ->
            val nonce = digest.leafNonce(index)
            digest.leafHash(index, nonce, bytes)
        }
    }

    /**
     * We calculate the tree's elements here from starting with the lowest level and progressing level by level towards
     * the root element.
     *
     * hashes contain the current level what we are process
     * nodeHashes contains the next level what we are calculating now
     * hashSet will be the result
     *
     * If any level has odd number of elements, the last one just gets lifted to the next level without
     * more hashing.
     */

    private val nodeHashes: List<List<SecureHash>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val hashSet = mutableListOf<List<SecureHash>>()
        var hashes = leafHashes
        hashSet += hashes
        var depthCounter = depth
        while (hashes.size > 1) {
            --depthCounter
            val nodeHashes = mutableListOf<SecureHash>()
            for (i in hashes.indices step 2) {
                if (i <= hashes.size - 2) {
                    nodeHashes += digest.nodeHash(depthCounter, hashes[i], hashes[i + 1])
                }
            }
            if ((hashes.size and 1) == 1) { // Non-paired last elements of odd lists, just get lifted one level upper.
                nodeHashes += hashes.last()
            }
            hashes = nodeHashes
            hashSet += hashes
        }
        require(depthCounter == 0) { "Sanity check root is at depth 0" }
        hashSet
    }

    private val depth: Int by lazy(LazyThreadSafetyMode.PUBLICATION) {
        treeDepth(leaves.size)
    }

    private val _root: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        nodeHashes.last().single()
    }

    override fun getLeaves() = leaves

    override fun getDigest() = digest

    override fun getRoot() = _root

    /**
     * createAuditProof creates a MerkleProof object that has a specified subset of the data leaves in a tree,
     * and the information needed to prove that those data leaves are actually members of the tree. This is
     * invoked on a fullly known Merkle Tree, and so we are hiding all the information we can.
     *
     * The proof contains
     * - the data what we are proving with their positions and nonce. (leaves)
     * - enough hashes of the other elements to be able to reconstruct the tree.
     * - the size of the tree.
     *
     * In particular, it is not viable to work out the data we are choosing not to reveal from the Merkle proof.
     *
     * The extra hashes' order is critical to fill the gaps between the subject elements.
     *
     * @param leafIndices a list of the leaf indexes that we do want to include in the original version
     * @return The MerkleProof object
     *
     */
    @Suppress("NestedBlockDepth", "ComplexMethod")
    override fun createAuditProof(leafIndices: List<Int>): MerkleProof {

        // We don't handle empty leafIndices; we could just say "yes you proved it" and not handle this special case
        // but it isn't useful.
        require(leafIndices.isNotEmpty()) { "Proof requires at least one leaf" }
        require(leafIndices.all { it >= 0 && it < leaves.size }) { "Leaf indices out of bounds" }
        require(leafIndices.toSet().size == leafIndices.size) {"Duplications are not allowed."}

        // We'll need to calculate the node's hashes on the routes from the subject elements
        // towards the tree's root element in the verification process.
        // We'll mark the indices of these route elements in inPath for the level what we are processing.
        // Through the processing of a level we'll add a set of hashes to the proof, and we'll calculate the next
        // level in route elements (next iteration's inPath) in the newInPath variable.

        var inPath = List(leaves.size) { it in leafIndices }    // Initialize inPath from the input elements
        val outputHashes = mutableListOf<SecureHash>()

        // When we process a level of the tree, we pair the elements, and we'll have these cases:
        // - Both elements of the pair are in route, which means they'll be calculated in the verification, so
        //   their parent on the next level will be in route as well, and we do not need to add their hashes.
        // - None of the elements of the pair are in route, which means their parent won't be either, also
        //   we do not need to add their hashes, since their parent hash will be enough to cover them.
        // - Only one of the elements are in route, which means their parent will be in route, and also we need to
        //   add the other element's hash to the proof.
        var level = 0
        while (inPath.size > 1) {
            val newInPath = mutableListOf<Boolean>()            // This will contain the next
                                                                // level's in route element's
            for (i in inPath.indices step 2) {
                if (i <= inPath.size - 2) {                     // We still have a pair to process.
                    newInPath += inPath[i] || inPath[i + 1]     // If any are in route, then their parent will be too
                    if (!inPath[i] && inPath[i + 1]) {          // We need to add a hash for the "Only one" cases.
                        outputHashes += nodeHashes[level][i]
                    } else if (inPath[i] && !inPath[i + 1]) {
                        outputHashes += nodeHashes[level][i + 1]
                    }
                }
            }
            // If a level has odd number of elements, then the last element is essentially the both or none case.
            // so we do not need to add its hash, and its parent node will be on the route if and only if it was on route.

            if ((inPath.size and 1) == 1) {                     // If the level has odd number of elements,
                                                                // the last one is still to be processed.
                newInPath += inPath.last()
            }
            inPath = newInPath
            ++level
        }
        require(level == depth) { "Sanity check calc" }
        return MerkleProofImpl(
            MerkleProofType.AUDIT,
            leaves.size,
            leafIndices.sorted().map { IndexedMerkleLeafImpl(it, digest.leafNonce(it), leaves[it].copyOf()) },
            outputHashes
        )
    }

    override fun toString(): String {
        val hashes = mutableMapOf<Pair<Int, Int>, String>()
        (0 until leaves.size).forEach { y ->
            (0..depth).forEach { x ->
                val nodeHashesLevel = nodeHashes.getOrNull(depth-x)?: emptyList()
                val hash = nodeHashesLevel.getOrNull(y)

                hashes[x to y] =  (hash?.toString()?:"").substringAfter(":").take(8).lowercase()
            }
        }
        return renderTree(leaves.size, leaves.map { " " + it.joinToString(separator = "") { b -> "%02x".format(b) } }, hashes)
    }
}



fun renderTree(treeSize: Int, des: List<String>, labels: Map<Pair<Int, Int>, String> = emptyMap()): String {
    var values: MutableList<Pair<Int, Int>> = (0 until treeSize).map { it to it }.toMutableList()
    val levels: MutableList<List<Pair<Int, Int>>> = mutableListOf(values.toList())
    while (values.size > 1) {
        val newValues: MutableList<Pair<Int, Int>> = mutableListOf()
        var index = 0 // index into node hashes, which starts off with an entry per leaf
        while (index < values.size) {
            if (index < values.size - 1) {
                // pair the elements
                newValues += Pair(values[index].first, values[index + 1].second)
                index += 2
            } else {
                // promote the odd man out
                newValues += values[index]
                index++
            }
        }
        levels += newValues.toList()
        check(newValues.size < values.size)
        values = newValues
    }
    val grid: MutableMap<Pair<Int, Int>, Char> = mutableMapOf()

    levels.forEachIndexed { level, ranges ->
        ranges.forEach { range ->
            val x = levels.size - level - 1
            grid.put(x to range.first, '━')
        }
    }
    levels.forEachIndexed { level, ranges ->
        ranges.forEach { range ->
            val x = levels.size - level - 1
            if (range.first != range.second) {
                val extent = if (level > 0) {
                    val nextLevel = levels[level - 1]
                    nextLevel.first { child -> range.second >= child.first && range.second <= child.second }.first
                } else range.second
                check(range.first <= extent)
                if (range.first != extent) {
                    val curtop = grid.getOrDefault(x to range.first, ' ')
                    grid[x to range.first] = when (curtop) {
                        '━' -> '┳'
                        else -> '┃'
                    }
                    for (y in range.first + 1 until extent) {
                        grid[x to y] = '┃'
                    }
                    grid[x to extent] = '┗'
                }
            }
        }
    }

    val longestLabels = (0..values.size + 1).map { x ->
        (0 until treeSize).map { y ->
            (labels.get(x to y) ?: "").length
        }.max()
    }
    println("longest labels $longestLabels")
    val lines = (0 until treeSize).map { y ->
        val line = (0..values.size + 1).map { x ->
            "${labels[x to y]?.padEnd(longestLabels[x], ' ')?:(" ".repeat(longestLabels[x]))}${grid.getOrDefault(x to y, ' ')}"
        }
        val label: String = des.getOrNull(y) ?: ""
        "${line.joinToString("")}$label"
    }

    return lines.joinToString("\n")
}
