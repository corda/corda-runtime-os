package net.corda.membership.impl

import net.corda.data.WireKeyValuePair
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.application.node.MemberContext
import net.corda.v5.application.node.MemberInfo

fun convertToMemberInfo(memberContext: MemberContext, mgmContext: MemberContext, keyEncodingService: KeyEncodingService): MemberInfo {
    return MemberInfoImpl(memberContext, mgmContext, keyEncodingService)
}

fun validateKeyOrder(original: List<WireKeyValuePair>) {
    val originalKeys = original.map { it.key }
    val sortedKeys = originalKeys.sortedBy { it }
    if (originalKeys != sortedKeys) {
        throw IllegalArgumentException("The input was manipulated as it's expected to be ordered by first element in pairs.")
    }
}

fun List<WireKeyValuePair>.convertToContext(): MemberContext {
    // before returning the ordered map, do the validation of ordering
    // (to avoid malicious attacks where extra data is attached to the end of the context)
    validateKeyOrder(this)
    return MemberContextImpl(this.map { it.key to it.value }.toMap().toSortedMap())
}