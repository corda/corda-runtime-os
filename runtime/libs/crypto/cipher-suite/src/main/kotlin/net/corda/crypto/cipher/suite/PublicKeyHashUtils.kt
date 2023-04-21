package net.corda.crypto.cipher.suite

import net.corda.v5.base.util.EncodingUtils
import net.corda.v5.crypto.DigestAlgorithmName
import java.security.MessageDigest
import java.security.PublicKey

private fun messageDigestSha256(): MessageDigest =
    MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

fun ByteArray.sha256Bytes(): ByteArray = messageDigestSha256().digest(this)

fun PublicKey.sha256Bytes(): ByteArray = messageDigestSha256().digest(encoded)

fun PublicKey.toStringShort(): String = "DL" + EncodingUtils.toBase58(sha256Bytes())