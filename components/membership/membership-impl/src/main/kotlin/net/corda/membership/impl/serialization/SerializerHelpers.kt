package net.corda.membership.impl

import net.corda.data.WireKeyValuePair
import net.corda.v5.application.node.KeyValueStore
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.application.node.MemberInfo

fun toMemberInfo(memberContext: KeyValueStore, mgmContext: KeyValueStore): MemberInfo {
    return MemberInfoImpl(memberContext, mgmContext)
}

fun validateKeyOrder(original: List<WireKeyValuePair>) {
    val originalKeys = original.map { it.key }
    val sortedKeys = originalKeys.sortedBy { it }
    if (originalKeys != sortedKeys) {
        throw IllegalArgumentException("The input was manipulated as it's expected to be ordered by first element in pairs.")
    }
}

fun List<WireKeyValuePair>.toKeyValueStore(keyEncodingService: KeyEncodingService): KeyValueStore {
    // before returning the ordered map, do the validation of ordering
    // (to avoid malicious attacks where extra data is attached to the end of the context)
    validateKeyOrder(this)
    return KeyValueStoreImpl(this.associate { it.key to it.value }.toSortedMap(), keyEncodingService)
}