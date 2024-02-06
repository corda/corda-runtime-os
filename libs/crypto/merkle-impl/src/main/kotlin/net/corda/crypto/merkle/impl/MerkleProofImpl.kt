package net.corda.crypto.merkle.impl

import net.corda.crypto.cipher.suite.merkle.MerkleProofInternal
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofRebuildFailureException
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest
import kotlin.math.min

/**
 * Represent a merkle proof, which shows that some leaf data is in a Merkle tree.
 *
 * @param proofType
 * @param treeSize - total number of leaves in the Merkle tree
 * @param leaves - list of leaves for which we have data
 * @param hashes - the input hashes needed to rebuild the parts of the tree where data is not given
 *
 * The number of elements in hashes will depend on the tree size and where in the tree the unknown
 * data is. There will need to be at least one to cover the gap left by missing data. There never
 * needs to be more than the tree size minus the number of leaves specified.
 */

class MerkleProofImpl(
    private val proofType: MerkleProofType,
    private val treeSize: Int,
    private val leaves: List<IndexedMerkleLeaf>,
    private val hashes: List<SecureHash>
) : MerkleProofInternal {
    // CORE-5111: add serialize/deserialize (and its test)

    override fun verify(root: SecureHash, digest: MerkleTreeHashDigest) =
        try {
            calculateRoot(digest) == root
        } catch (e: Exception) {
            false
        }

    @Suppress("NestedBlockDepth", "ComplexMethod")
    /**
     * The verification process reconstructs the root element of the Merkle tree from the proof.
     * Then it compares it with the input parameter to check if they match.
     *
     * It walks through the levels of the tree from bottom to up following the same logic as the
     * proof or tree creation. [MerkleTreeImpl.createAuditProof]
     * It recreates the routes towards the root element from the items in the leaves to be proven with using
     * the proof hashes when they are needed.
     *
     * @param digest the digest service to use, which must generate hashes compatible with the [hashes] constructor parameter.
     * @return the secure hash of the root of the Merkle proof
     */
    override fun calculateRoot(digest: MerkleTreeHashDigest): SecureHash = calculateRootInstrumented(digest)

    /**
     * Walk the Merkle proof, making a callback on each node that is derived along the way, and return
     * the root hash if the proof is valid.
     *
     * @param digest the digest service to use, which must generate hashes compatible with the [hashes] constructor parameter.
     * @param onNewHash called with information about a node taken from incoming hashes or calculated during the proof.
     *                  This will be called left to right then bottom to top, i.e., the same order as input hashes are consumed.
     * @return the secure hash of the root of the Merkle proof
     */
    @Suppress("NestedBlockDepth", "ThrowsCount")

    private fun calculateRootInstrumented(
        digest: MerkleTreeHashDigest,
        onNewHash: (info: MerkleNodeInfo) -> Unit = {_ -> }
    ): SecureHash {
        if (digest !is MerkleTreeHashDigestProvider) {
            throw CordaRuntimeException(
                "An instance of MerkleTreeHashDigestProvider is required when " +
                        "verifying a Merkle root, but received ${digest.javaClass.name} instead."
            )
        }

        // We do support and test with leaves.size == treeSize, which may not
        // be very useful but needed not be a special case.
        if (leaves.size > treeSize) {
            throw MerkleProofRebuildFailureException("MerkleProof has too many specified keys ${leaves.size} tree size $treeSize")
        }
        if (leaves.size < treeSize && hashes.isEmpty()) {
            throw MerkleProofRebuildFailureException("No fill-in hashes specified to MerkleProof")
        }
        if (hashes.size > treeSize + leaves.size) {
            throw MerkleProofRebuildFailureException("More MerkleProof non-data hashes given than is possibly necessary")
        }
        if (leaves.isEmpty()) {
            throw MerkleProofRebuildFailureException("MerkleProof should have at least one leaf.")
        }
        if (leaves.any { it.index < 0 || it.index >= treeSize }) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof leaves cannot point outside of the original tree."
            )
        }
        if (leaves.map { it.index }.toSet().size != leaves.size) {
            throw MerkleProofRebuildFailureException("MerkleProof leaves cannot have duplications.")
        }
        var hashIndex = 0
        var treeDepth = MerkleTreeImpl.treeDepth(treeSize)         // initialised to the depth of tree we should
        // work out nodeHashes, which is a list of node information for the current level we operate at
        var nodeHashes: List<MerkleNode> = leaves.sortedBy { it.index }.map {
            MerkleNode(it.index, digest.leafHash(it.index, it.nonce, it.leafData))
        }

        // We will discover some hashes at a given level and some at the level above.
        // But we want to guarantee to our uses that they get out hashes on the `onNewHash` callback
        // in a left to right then bottom to top order, rather than mix up leaves from different levels.
        // That way, the user code is simpler. So, we need to efficiently sort the output order.

        // Maintain the set of nodes from the current level, to be interspersed on the next level.
        var pendingNodes = nodeHashes.map { MerkleNodeInfo(it, treeDepth, null) }.toMutableList()

        // A procedure to run the onNewHash callback on all nodes we should output before we output a specific
        // node.
        fun publishBelow(index: Int) {
            while (pendingNodes.isNotEmpty() && pendingNodes.first().node.indexWithinLevel < index) {
                onNewHash(pendingNodes.removeFirst())
            }
        }
        var currentSize = treeSize                                 // outer loop variable; the number of
                                                                   // leaves left as we roll up the tree

        // loop over each level of the tree, starting at the deepest level (i.e., furthest from root)
        while (currentSize > 1) {
            val newPendingNodes = mutableListOf<MerkleNodeInfo>()
            // Process a level of the tree which means generating the hashes for the level above (i.e., closer
            // to the root).

            // There is nothing to do if the tree size $currentSize is 1, hence the loop condition
            if (nodeHashes.isEmpty()) {
                throw MerkleProofRebuildFailureException(
                    "MerkleProof does not have enough nodeHashes to calculate root hash."
                )
            }
            --treeDepth
            // We could check here that the size of nodeHashes is as expected for treeDepth; there should be a closed form.

            // ... so that's 4 variables that get updated as we work:
            // - $hashIndex is the position we are at in the supplied proof hashes
            // - $currentSize is the number of leafs+nodes at this level of the tree
            // - $treeDepth is the level of the tree, counting from the root of the tree where $currentSize==1
            // - $nodeHashes has a list of pairs of the index and hash of the node. We checked we have some content.

            val newItems = mutableListOf<MerkleNode>()   // this will become nodeHashes at the end of this
            // out iteration

            // Now walk over the hashes at this tree level, striding over 1 or 2 at a time
            var index = 0

            while (index < nodeHashes.size) {
                val item = nodeHashes[index]
                // We are at level $treeDepth from the top of the tree (where 1 is the root of the tree),
                //     and at $index nodes from the left (counting from 0)
                // $item is a pair of the index and the hash at the index.
                //
                // Since index == item.first we don't really need to use item.first

                if (item.indexWithinLevel < currentSize and 0x7FFFFFFE) {      // If the level has odd elements, we'll process
                    // the last element later.
                    if (index < nodeHashes.size - 1) {              // If there is a next element...
                        val next = nodeHashes[index + 1]
                        // Decide if we can consume the next two elements since they are adjacent in the Merkle tree
                        if (item.indexWithinLevel xor next.indexWithinLevel == 1) {       // ... and they are a pair with the current
                            // We now know that the indices ${item.first} and ${next.first} only differ on the bottom bit,
                            // i.e., they are adjacent. Therefore, we can combine them.

                            // So, make a single new item, computing a new hash
                            // (Pair is the Kotlin type, nothing to do with pairing nodes)
                            // in the original tree, we create their parent.
                            val newHash = digest.nodeHash(treeDepth, item.hash, next.hash)
                            val newNode = MerkleNode(item.indexWithinLevel / 2, newHash)
                            newPendingNodes.add(MerkleNodeInfo(newNode, treeDepth, null))
                            newItems.add(newNode)
                            // and record that we consumed two values from our working set, and skip on to the
                            // start of the next loop
                            index += 2                              // we've consumed two
                            continue                                // continue the inner level scanning loop
                        }
                    }
                    // The continue above in the previous section mean we skip the rest of this section if we
                    // chose to make a new node by combining two known hashes.

                    // At this point we know we do not know enough to simply take two known hashes at $index and ${index+1} and roll
                    // them up, so we are going to have to consume a hash.

                    if (hashIndex >= hashes.size) {                 // We'll need one more hash to continue. So if
                        throw MerkleProofRebuildFailureException(   // we do not have more, the proof is incorrect.
                            "MerkleProof root calculation requires more hashes than the proof has."
                        )
                    }

                    // Make up a new node, which will be a level up so will have a node index shifted right one
                    val newIndex = item.indexWithinLevel / 2

                    // We pair the current element with a hash from the proof
                    val newNode = (if ((item.indexWithinLevel and 1) == 0) {      // Even index means, that the item is on the left
                        // Remember we consumed a proof hash for the right hand child
                        publishBelow(item.indexWithinLevel+1)
                        onNewHash(MerkleNodeInfo(MerkleNode(item.indexWithinLevel + 1, hashes[hashIndex]), treeDepth + 1, hashIndex))
                        // Make new node with
                        //   - left being current element
                        //   - right being a consumed incoming hash from $hashes[$hashIndex]
                        val newHash = digest.nodeHash(treeDepth, item.hash, hashes[hashIndex])
                        MerkleNode(newIndex, newHash)
                    } else {
                        // Remember we consumed a proof hash for the left hand child
                        publishBelow(item.indexWithinLevel-1)
                        onNewHash(MerkleNodeInfo(MerkleNode(item.indexWithinLevel - 1, hashes[hashIndex]), treeDepth + 1, hashIndex))
                        // Make new node with:
                        //   - left being proof of hash at $hashIndex
                        //   - right being current element, index $item.first, hash $item.second
                        val newHash = digest.nodeHash(treeDepth, hashes[hashIndex], item.hash)
                        MerkleNode(newIndex, newHash)
                    })
                    hashIndex++ // Remember we used an incoming hash
                    newPendingNodes.add(MerkleNodeInfo(newNode, treeDepth, null))
                    newItems.add(newNode)
                } else {
                    // The last odd element, just gets lifted.
                    val newIndex = (item.indexWithinLevel + 1) / 2
                    val newNode = MerkleNode(newIndex, item.hash)
                    newPendingNodes.add(MerkleNodeInfo(newNode, treeDepth, null))
                    newItems += MerkleNode(newIndex, item.hash)
                }
                index++ // whatever of the last 3 cases we took, we consumed one node
            }
            // now we move up a level, so the tree gets smaller...
            currentSize = (currentSize + 1) / 2
            // and we have a new set of known elements
            nodeHashes = newItems
            publishBelow(Int.MAX_VALUE)
            pendingNodes = newPendingNodes
        }
        if (hashIndex != hashes.size) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof root hash calculation used $hashIndex hashes instead of the available ${hashes.size}."
            )
        }
        if (nodeHashes.size != 1) {
            throw MerkleProofRebuildFailureException(
                "MerkleProof root hash calculation ended with ${nodeHashes.size} node hashes instead of one."
            )
        }
        publishBelow(Int.MAX_VALUE)

        return nodeHashes.single().hash
    }

    override fun subset(digest: MerkleTreeHashDigest, leafIndices: List<Int>): MerkleProofImpl {
        val outLeaves = leaves.filter { it.index in leafIndices }
        require(outLeaves.size == leafIndices.size) { "some leaves are not available in input proof"}
        require(outLeaves.isNotEmpty()) { "output proof must have at least one known leaf"}
        // we will build up a set of available leaves as we track. This will start out with the known leaves
        // in the output subset proof, and will be augmented as we decide to add output hashes to the proof
        val availableLeaves = leafIndices.toMutableSet()
        // We work out the hashes for the new subset proof by considering, for the original proof, each hash that
        // is calculated when we verify the proof by calculating the root.
        val outHashes: MutableList<SecureHash> = mutableListOf()
        val treeDepth = MerkleTreeImpl.treeDepth(treeSize)
        calculateRootInstrumented(digest) { info ->
            val height = treeDepth - info.level // how many levels above the leaves, 0 for being at the leaf
            val leftmostLeaf = info.node.indexWithinLevel shl height
            val afterLeaf = min(leftmostLeaf + (1 shl height), treeSize)
            val numberOfUnknowns = (leftmostLeaf until afterLeaf).count { it !in availableLeaves }
            val allUnknown = numberOfUnknowns == (afterLeaf-leftmostLeaf)
            val unknowns = numberOfUnknowns > 0
            // work out the sibling node i.e. the node which shares a direct parent node with this one
            val siblingIndex = info.node.indexWithinLevel xor 1
            val siblingLeftmostLeaf = siblingIndex shl height
            val afterSiblingLeaf = min(siblingLeftmostLeaf + (1 shl height), treeSize)
            val siblingWillBeKnown = (siblingLeftmostLeaf until afterSiblingLeaf).any { it in availableLeaves }
            // we need this hash in the subset proof if and only if it covers unknown content ($unknowns is true),
            // and either there is:
            // - some known leaves under this node ($allUnknown is false)
            // - the sibling node has known leaves ($siblingWillBeKnown is true)
            if (unknowns && (siblingWillBeKnown || !allUnknown)) {
                outHashes.add(info.node.hash)
                // now we have decided to take $hash so all the leaves it covers will be known
                for (i in leftmostLeaf until afterLeaf) availableLeaves.add(i)
            }
        }
        return MerkleProofImpl(proofType, treeSize, outLeaves, outHashes)
    }

    @Suppress("UnusedParameters")
    override fun merge(other: MerkleProof, digest: MerkleTreeHashDigestProvider): MerkleProofInternal {
        require(this.treeSize == other.treeSize) {
            "underlying tree sizes must match; left hand side has underlying tree "+
                "size ${this.treeSize} and right hand side has underlying tree size ${other.treeSize}"
        }

        // First, work out the leaves for the output proof.
        val indexMapThis = leaves.associateBy { it.index }
        val indexMapOther = other.leaves.associateBy { it.index }
        // We should fail early, at this point, if any leaves in both mismatch.
        indexMapThis.forEach { (index, leaf) ->
            require(indexMapOther.getOrDefault(index, leaf) == leaf) {
                "common leaves in a proof merge must match"
            }
        }
        val combinedIndexMap = indexMapThis + indexMapOther
        val outLeaves = combinedIndexMap.values.toList().sortedWith( compareBy { it.index })

        // Second, walk the whole proof structure, for both this and other.
        val nodeMapThis: MutableMap< Pair<Int, Int>, MerkleNodeInfo> = mutableMapOf()
        val thisRoot = calculateRootInstrumented(digest) {
            val k = it.level to it.node.indexWithinLevel
            nodeMapThis[k] = it
        }
        val nodeMapOther: MutableMap< Pair<Int, Int>, MerkleNodeInfo> = mutableMapOf()
        val otherRoot = (other as MerkleProofImpl).calculateRootInstrumented(digest) {
            val k = it.level to it.node.indexWithinLevel
            nodeMapOther[k] = it
        }
        require(thisRoot == otherRoot) { "left hand and right hand size proof root hashes do not match"}

        // Third, walk the whole tree and figure out what to do based on what we know.
        val outHashes = mutableListOf<SecureHash>() // the goal is to fill this in
        val levels = makeLevels(treeSize)
        levels.forEachIndexed { height, ranges ->
            val level = levels.size - height - 1
            for (indexWithinLevel in ranges.indices) {
                val k = level to indexWithinLevel
                val x = nodeMapThis[k]
                val y = nodeMapOther[k]

                // For each node, where x is me and y is the other proof, and o is the output proof
                //    (so we're doing O = X∪Y)
                // if x is calculated, it will be calculable in o, so no proof hash needed
                //  or if y is calculated, it will be calculable in o, so no proof hash needed
                //    or else, we now know that we will need a proof hash since neither X or Y has that value.
                //    if x uses a proof hash, add that proof hash for o
                //      or if y uses a proof hash, add that proof hash for o
                //         else it is unknown in both, so it is unknown

                when {
                    x != null && x.consumed == null -> {
                        // x is calculated, so it can be calculated in o, no proof hash needed in O
                    }
                    y != null && y.consumed == null -> {
                        // y is calculated, so it can be calculated in o, no proof hash needed in O
                    }
                    x?.consumed != null -> {
                        // even if y consumed a proof it doesn't matter whether we take x.node.hash or y.node.hash
                        // since they are identical

                        if (y?.consumed != null)
                            check(x.node.hash == y.node.hash)
                        outHashes += x.node.hash
                    }
                    y?.consumed != null -> {
                        outHashes += y.node.hash
                    }
                    else -> {
                        check(x == null)
                        check(y == null)
                    }
                }
            }
        }
        return MerkleProofImpl(proofType, treeSize, outLeaves, outHashes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MerkleProofImpl

        if (treeSize != other.treeSize) return false
        if (leaves != other.leaves) return false
        if (hashes != other.hashes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = treeSize
        result = 31 * result + leaves.hashCode()
        result = 31 * result + hashes.hashCode()
        return result
    }

    override fun getProofType() = proofType

    override fun getTreeSize() = treeSize

    override fun getLeaves() = leaves

    override fun getHashes() = hashes

    override fun render(digest: MerkleTreeHashDigest): String {
        val nodeLabels: MutableMap<Pair<Int,Int>, String> = mutableMapOf()
        val treeDepth = MerkleTreeImpl.treeDepth(treeSize)         // initialised to the depth of tree we should
        for(x in 0..treeDepth)
            for (y in 0 until (1 shl x)) // should be 0 until x?
                nodeLabels[x to y] = "unknown"
        calculateRootInstrumented(digest) { info ->
            nodeLabels[info.level to info.node.indexWithinLevel] = info.node.hash.toString().substringAfter(":")
                .take(8) + (if (info.consumed!=null) " (input ${info.consumed})" else " (calc)")
        }
        val leafLabels =  (0 until treeSize).map {
            if (it in getLeaves().map { l -> l.index }) "known leaf" else "filtered"
        }
        return renderTree(leafLabels, nodeLabels)
    }
}
