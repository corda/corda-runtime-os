package net.corda.v5.cipher.suite.schemes

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.MessageDigest

class DigestServiceMock : DigestService {
    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash =
        SecureHash(digestAlgorithmName.name, MessageDigest.getInstance(digestAlgorithmName.name).digest(bytes))

    override fun hash(inputStream: InputStream, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val messageDigest = MessageDigest.getInstance(digestAlgorithmName.name)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while(true) {
            val read = inputStream.read(buffer)
            if(read <= 0) break
            messageDigest.update(buffer, 0, read)
        }
        return SecureHash(digestAlgorithmName.name, messageDigest.digest())
    }

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
        MessageDigest.getInstance(digestAlgorithmName.name).digestLength
}