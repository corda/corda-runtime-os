package net.corda.libs.packaging.tests.legacy

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

// WARNING - "legacy" corda5 code *only* used to make tests pass.
class DigestServiceImpl : DigestService {
    private val messageDigests: ConcurrentMap<String, DigestSupplier> = ConcurrentHashMap()

    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestAs(bytes, digestAlgorithmName)
        return SecureHash(digestAlgorithmName.name, hashBytes)
    }

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

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int {
        return digestFor(digestAlgorithmName).digestLength
    }

    private fun digestAs(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): ByteArray {
        return digestFor(digestAlgorithmName).get().digest(bytes)
    }

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestSupplier {
        return messageDigests.getOrPut(digestAlgorithmName.name) { DigestSupplier(digestAlgorithmName.name) }
    }
}
