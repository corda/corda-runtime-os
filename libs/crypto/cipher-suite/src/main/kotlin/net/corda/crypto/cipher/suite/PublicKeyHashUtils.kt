package net.corda.crypto.cipher.suite

import net.corda.v5.base.util.ByteArrays
import net.corda.v5.base.util.EncodingUtils
import net.corda.v5.crypto.DigestAlgorithmName
import java.security.MessageDigest
import java.security.PublicKey

private fun messageDigestSha256(): MessageDigest =
    MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

fun ByteArray.sha256Bytes(): ByteArray = messageDigestSha256().digest(this)

fun PublicKey.sha256Bytes(): ByteArray = messageDigestSha256().digest(encoded)

fun PublicKey.toStringShort(): String = "DL" + EncodingUtils.toBase58(sha256Bytes())

fun PublicKey.sha256HexString(): String = ByteArrays.toHexString(sha256Bytes())

//  This is a helper functions copying the usage of ShortHash.of function to avoid moving ShortHash to cipher suite
fun PublicKey.getShortHashString(): String {
    val hexString = sha256HexString()
    if (hexString.length < 12) {
        throw ShortHashStringException("Hex string has length of ${hexString.length} but should be at least 12 characters")
    }
    if (!isHexString(hexString)) {
        throw ShortHashStringException("Not a hex string: '$hexString'")
    }
    return hexString.substring(0, 12).uppercase()
}
private fun isHexString(hexString: String): Boolean =
    hexString.matches(Regex("[0-9a-fA-F]+"))

/** Exception thrown if creation of a short hash string fails */
class ShortHashStringException(message: String?, cause: Throwable? = null) : Exception(message, cause)
