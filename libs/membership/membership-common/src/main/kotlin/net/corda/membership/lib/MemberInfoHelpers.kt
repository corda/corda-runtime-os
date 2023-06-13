package net.corda.membership.lib

import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import java.util.*

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
    return items.associate { it.key to it.value }.toSortedMap()
}

/**
 * Recreates the signature spec based on the signature's name.
 */
fun retrieveSignatureSpec(signatureName: String) = if (signatureName.isEmpty()) {
    CryptoSignatureSpec("", null, null)
} else {
    CryptoSignatureSpec(signatureName, null, null)
}
