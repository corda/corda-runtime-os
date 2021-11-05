package net.corda.membership.identity

import net.corda.data.WireKeyValuePair
import net.corda.v5.membership.identity.MGMContext
import net.corda.v5.membership.identity.MemberContext
import net.corda.v5.membership.identity.MemberInfo
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
fun validateKeyOrder(original: List<WireKeyValuePair>) {
    val originalKeys = original.map { it.key }
    val sortedKeys = originalKeys.sortedBy { it }
    if (originalKeys != sortedKeys) {
        throw IllegalArgumentException("The input was manipulated as it's expected to be ordered by first element in pairs.")
    }
}

/**
 * Recreates the sorted map structure after deserialization.
 */
fun List<WireKeyValuePair>.toSortedMap(): SortedMap<String, String?> {
    // before returning the ordered map, do the validation of ordering
    // (to avoid malicious attacks where extra data is attached to the end of the context)
    validateKeyOrder(this)
    return this.associate { it.key to it.value }.toSortedMap()
}