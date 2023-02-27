package net.corda.crypto.impl

import net.corda.crypto.core.OID_COMPOSITE_KEY_IDENTIFIER
import net.corda.utilities.exactAdd
import net.corda.v5.base.types.ByteArrays.sequence
import net.corda.v5.crypto.COMPOSITE_KEY_CHILDREN_LIMIT
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.crypto.keys
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Primitive
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence

import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.PublicKey
import java.util.IdentityHashMap

val PublicKey.keys: Set<PublicKey> get() = (this as? CompositeKey)?.leafKeys ?: setOf(this)

fun CompositeKeyNodeAndWeight.toASN1Primitive(): ASN1Primitive {
    val vector = ASN1EncodableVector()
    vector.add(DERBitString(node.encoded))
    vector.add(ASN1Integer(weight.toLong()))
    return DERSequence(vector)
}

class CompositeKeyImpl(val threshold: Int, childrenUnsorted: List<CompositeKeyNodeAndWeight>) : CompositeKey {
    companion object {
        const val KEY_ALGORITHM = "COMPOSITE"

        // Required for sorting [children] list. To ensure a deterministic way of adding children required for equality
        // checking, [children] list is sorted during construction. A DESC ordering in the [NodeAndWeight.weight] field
        // will improve efficiency, because keys with bigger "weights" are the first to be checked and thus the
        // threshold requirement might be met earlier without requiring a full [children] scan.
        private val descWeightComparator =
            compareBy<CompositeKeyNodeAndWeight>({ -it.weight }, { sequence(it.node.encoded) })

        fun createFromKeys(keys: List<PublicKey>, threshold: Int?) =
            create(keys.map { CompositeKeyNodeAndWeight(it, 1) }, threshold)

        fun create(keys: List<CompositeKeyNodeAndWeight>, threshold: Int?): PublicKey =
            when {
                keys.size > 1 -> CompositeKeyImpl(threshold ?: keys.sumOf { it.weight }, keys)
                keys.size == 1 -> {
                    require(threshold == null || threshold == keys.first().weight)
                    keys.first().node
                }
                else -> throw IllegalStateException("Trying to build CompositeKey without child nodes.")
            }
    }

    /**
     * List of children keys with their weight. Τhe order of the children may not be the same as that
     * provided in the builder.
     */
    val children: List<CompositeKeyNodeAndWeight> = childrenUnsorted.sortedWith(descWeightComparator)

    init {
        checkConstraints()
    }

    @Transient
    private var validated = false

    // Check for key duplication, threshold and weight constraints and test for aggregated weight integer overflow.
    private fun checkConstraints() {
        require(children.size == children.toSet().size) { "CompositeKey with duplicated child nodes detected." }
        // If we want PublicKey we only keep one key, otherwise it will lead to semantically equivalent trees
        // but having different structures.
        require(children.size > 1) { "CompositeKey must consist of two or more child nodes." }
        require(children.size <= COMPOSITE_KEY_CHILDREN_LIMIT) {
            "CompositeKey must consist of less or equal than $COMPOSITE_KEY_CHILDREN_LIMIT child nodes."
        }
        // We should ensure threshold is positive, because smaller allowable weight for a node key is 1.
        require(threshold > 0) {
            "CompositeKey threshold is set to $threshold, but it should be a positive integer."
        }
        // If threshold is bigger than total weight, then it will never be satisfied.
        val totalWeight = totalWeight()
        require(threshold <= totalWeight) {
            "CompositeKey threshold: $threshold cannot be bigger than aggregated weight of child nodes: $totalWeight"
        }
    }

    // Graph cycle detection in the composite key structure to avoid infinite loops on CompositeKey graph traversal and
    // when recursion is used (i.e. in isFulfilledBy()).
    // An IdentityHashMap Vs HashMap is used, because a graph cycle causes infinite loop on the CompositeKey.hashCode().
    private fun cycleDetection(visitedMap: IdentityHashMap<CompositeKeyImpl, Boolean>) {
        for ((node) in children) {
            if (node is CompositeKeyImpl) {
                val curVisitedMap = IdentityHashMap<CompositeKeyImpl, Boolean>()
                curVisitedMap.putAll(visitedMap)
                // We can't print the node details, because doing so involves serializing the node, which we can't
                // do because of the cyclic graph.
                require(!curVisitedMap.contains(node)) { "Cycle detected for CompositeKey" }
                curVisitedMap[node] = true
                node.cycleDetection(curVisitedMap)
            }
        }
    }

    /**
     * This method will detect graph cycles in the full composite key structure to protect against infinite loops when
     * traversing the graph and key duplicates in each layer. It also checks if the threshold and weight constraint
     * requirements are met, while it tests for aggregated-weight integer overflow.
     * In practice, this method should be always invoked on the root [CompositeKey], as it inherently
     * validates the child nodes (all the way till the leaves).
     */
    override fun checkValidity() {
        if (validated) return
        val visitedMap = IdentityHashMap<CompositeKeyImpl, Boolean>()
        visitedMap[this] = true
        cycleDetection(visitedMap) // Graph cycle testing on the root node.
        checkConstraints()
        for ((node, _) in children) {
            if (node is CompositeKeyImpl) {
                // We don't need to check for cycles on the rest of the nodes (testing on the root node is enough).
                node.checkConstraints()
            }
        }
        validated = true
    }

    // Method to check if the total (aggregated) weight of child nodes overflows.
    // Unlike similar solutions that use long conversion, this approach takes advantage of the minimum weight being 1.
    private fun totalWeight(): Int {
        var sum = 0
        for ((_, weight) in children) {
            require(weight > 0) { "Non-positive weight: $weight detected." }
            sum = sum exactAdd weight // Add and check for integer overflow.
        }
        return sum
    }


    /**
     * Takes single [PublicKey] and checks if [CompositeKey] requirements hold for that key.
     */
    override fun isFulfilledBy(key: PublicKey) = isFulfilledBy(setOf(key))

    /**
     * Returns the standard algorithm name for this key, which is always "COMPOSITE".
     */
    override fun getAlgorithm() = KEY_ALGORITHM

    /**
     * Returns the key in its "DER" encoding format.
     */
    override fun getEncoded(): ByteArray {
        val keyVector = ASN1EncodableVector()
        val childrenVector = ASN1EncodableVector()
        children.forEach {
            childrenVector.add(it.toASN1Primitive())
        }
        keyVector.add(ASN1Integer(threshold.toLong()))
        keyVector.add(DERSequence(childrenVector))
        return SubjectPublicKeyInfo(AlgorithmIdentifier(OID_COMPOSITE_KEY_IDENTIFIER), DERSequence(keyVector)).encoded
    }

    /**
     * Returns the name of the primary encoding format of this key, which is always "DER".
     */
    override fun getFormat() = ASN1Encoding.DER

    // Return true when and if the threshold requirement is met.
    private fun checkFulfilledBy(keysToCheck: Iterable<PublicKey>): Boolean {
        var totalWeight = 0
        children.forEach { (node, weight) ->
            if (node is CompositeKeyImpl) {
                if (node.checkFulfilledBy(keysToCheck)) totalWeight += weight
            } else {
                if (node in keysToCheck) totalWeight += weight
            }
            if (totalWeight >= threshold) return true
        }
        return false
    }

    /**
     * Checks if the public keys corresponding to the signatures are matched against the leaves of the composite
     * key tree in question, and the total combined weight of all children is calculated for every intermediary node.
     * If all thresholds are satisfied, the composite key requirement is considered to be met.
     */
    override fun isFulfilledBy(keysToCheck: Iterable<PublicKey>): Boolean {
        // We validate keys only when checking if they're matched, as this checks sub keys as a result.
        // Doing these checks at deserialization/construction time would result in duplicate checks.
        checkValidity()
        if (keysToCheck.any { it is CompositeKey }) return false
        return checkFulfilledBy(keysToCheck)
    }

    /**
     * Set of all leaf keys of that [CompositeKey].
     */
    override val leafKeys: Set<PublicKey>
        get() = children.flatMap { it.node.keys }.toSet() // Uses PublicKey.keys extension.

    /**
     * Compares the two given instances of the [CompositeKey] based on the content.
     */
    override fun equals(other: Any?): Boolean =
        this === other || other is CompositeKeyImpl && threshold == other.threshold && children == other.children

    /**
     * Returns a hash code value for the object.
     */
    override fun hashCode(): Int = threshold * 31 + children.hashCode()

    /**
     * Converts a [CompositeKey] object to a string representation.
     */
    override fun toString() = "(${children.joinToString()})"
}

