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

// TODO The below needs to reference with `ShortHash` but can't do now because cipher-suite module can't depend
//  on crypto-core due to circular dependency issue
const val SHORT_KEY_ID_LENGTH = 12
/**
 * Returns the id as the first 12 characters of an SHA-256 hash from a given [PublicKey].
 */
fun PublicKey.publicKeyId(): String  {
    val fullKeyIdHex = ByteArrays.toHexString(sha256Bytes())
    return fullKeyIdHex.substring(0, SHORT_KEY_ID_LENGTH)
}