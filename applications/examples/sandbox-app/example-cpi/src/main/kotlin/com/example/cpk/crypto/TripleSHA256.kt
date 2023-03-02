package com.example.cpk.crypto

import net.corda.v5.crypto.DigestAlgorithmName
import java.io.InputStream
import java.security.MessageDigest
import net.corda.v5.crypto.DigestAlgorithmName.SHA2_256
import net.corda.v5.crypto.extensions.DigestAlgorithm
import net.corda.v5.crypto.extensions.DigestAlgorithmFactory

@Suppress("unused")
class TripleSHA256 : DigestAlgorithmFactory {
    override fun getAlgorithm() = TripleSHA256Digest.ALGORITHM

    override fun getInstance(): DigestAlgorithm = TripleSHA256Digest()
}

private class TripleSHA256Digest : DigestAlgorithm {
    companion object {
        const val ALGORITHM = "SHA-256-TRIPLE"
    }

    override fun getAlgortihm() = ALGORITHM
    override fun getDigestLength() = 32
    override fun digest(bytes: ByteArray): ByteArray = bytes.sha256Bytes().sha256Bytes().sha256Bytes()

    override fun digest(inputStream: InputStream): ByteArray {
        val messageDigest = MessageDigest.getInstance(SHA2_256.name)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead < 0) {
                break
            }
            messageDigest.update(buffer, 0, bytesRead)
        }
        return messageDigest.digest().sha256Bytes().sha256Bytes()
    }
}

private fun messageDigestSha256(): MessageDigest =
    MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)

fun ByteArray.sha256Bytes(): ByteArray = messageDigestSha256().digest(this)
