package net.corda.membership.identity

import net.corda.data.KeyValuePairList
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.MerkleTree
import net.corda.v5.membership.identity.MGMContext
import net.corda.v5.membership.identity.MemberContext
import net.corda.v5.membership.identity.MemberInfo
import java.nio.ByteBuffer
import java.util.SortedMap

/**
 * Recreates [MemberInfo] with [MemberContext] and [MGMContext] after avro deserialization.
 */
fun toMemberInfo(memberContext: MemberContext, mgmContext: MGMContext): MemberInfo {
    return MemberInfoImpl(memberContext, mgmContext)
}

/**
 * Validates the order of the key, we are making sure they are not tampered with.
 */
fun validateKeyOrder(original: KeyValuePairList) {
    val originalKeys = original.items.map { it.key }
    val sortedKeys = originalKeys.sortedBy { it }
    if (originalKeys != sortedKeys) {
        throw IllegalArgumentException("The input was manipulated as it's expected to be ordered by first element in pairs.")
    }
}

/**
 * Recreates the sorted map structure after deserialization.
 */
fun KeyValuePairList.toSortedMap(): SortedMap<String, String?> {
    // before returning the ordered map, do the validation of ordering
    // (to avoid malicious attacks where extra data is attached to the end of the context)
    validateKeyOrder(this)
    return this.items.map { it.key to it.value }.toMap().toSortedMap()
}

/**
 * Builds the Merkle tree used for the mgm's signature.
 * The leaves are the [MemberContext] and [MGMContext].
 */
fun buildMerkleTree(memberContext: ByteBuffer, mgmContext: ByteBuffer, digestService: DigestService): MerkleTree {
    val leaves = mutableListOf(
        digestService.hash(memberContext.array(), DigestAlgorithmName.SHA2_256),
        digestService.hash(mgmContext.array(), DigestAlgorithmName.SHA2_256)
    )
    return MerkleTree.getMerkleTree(leaves, DigestAlgorithmName.SHA2_256, digestService)
}