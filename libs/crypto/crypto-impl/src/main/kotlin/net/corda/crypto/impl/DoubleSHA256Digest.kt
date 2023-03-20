package net.corda.crypto.impl

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.extensions.DigestAlgorithm
import java.io.InputStream
import java.security.MessageDigest

class DoubleSHA256Digest : DigestAlgorithm {
    companion object {
        const val ALGORITHM = "SHA-256D"
        const val STREAM_BUFFER_SIZE = DEFAULT_BUFFER_SIZE
    }

    override fun getAlgorithm() = ALGORITHM
    override fun getDigestLength() = 32
    override fun digest(bytes: ByteArray): ByteArray = bytes.sha256Bytes().sha256Bytes()
    override fun digest(inputStream: InputStream): ByteArray {
        val messageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            messageDigest.update(buffer, 0, read)
        }
        return messageDigest.digest().sha256Bytes()
    }
}