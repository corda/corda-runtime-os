package net.corda.membership.lib

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.util.*
import kotlin.jvm.Throws

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

/**
 * Deserializes byte array (serialized as [KeyValuePairList]) used mainly as registration, member or MGM context.
 *
 * @throws ContextDeserializationException if deserialization failed
 */
@Throws(ContextDeserializationException::class)
fun ByteArray.deserializeContext(
    cordaAvroDeserializer: CordaAvroDeserializer<KeyValuePairList>,
): Map<String, String> {
    return cordaAvroDeserializer.deserialize(this)?.items?.associate { it.key to it.value }
        ?: throw ContextDeserializationException
}

object ContextDeserializationException :
    CordaRuntimeException("Failed to deserialize key value pair list.")
