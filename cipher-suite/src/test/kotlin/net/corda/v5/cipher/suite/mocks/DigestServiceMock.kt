package net.corda.v5.cipher.suite.mocks

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.security.MessageDigest

class DigestServiceMock : DigestService {
    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash =
        SecureHash(digestAlgorithmName.name, MessageDigest.getInstance(digestAlgorithmName.name).digest(bytes))

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int =
        MessageDigest.getInstance(digestAlgorithmName.name).digestLength
}