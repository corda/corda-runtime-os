package net.corda.simulator.runtime.hashing

import net.corda.crypto.core.SecureHashImpl
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.security.MessageDigest

class SimulatedDigestService : DigestService {
    override fun hash(bytes: ByteArray, digestName: DigestAlgorithmName): SecureHash {
        return SecureHashImpl(
            digestName.name,
            MessageDigest.getInstance(digestName.name).digest(bytes)
        )
    }

    override fun hash(inputStream: InputStream, digestName: DigestAlgorithmName): SecureHash {
        TODO("Not yet implemented")
    }

    override fun parseSecureHash(algoNameAndHexString: String): SecureHash {
        TODO("Not yet implemented")
    }

    override fun digestLength(digestName: DigestAlgorithmName): Int {
        TODO("Not yet implemented")
    }

    override fun defaultDigestAlgorithm(): DigestAlgorithmName {
        TODO("Not yet implemented")
    }

    override fun supportedDigestAlgorithms(): MutableSet<DigestAlgorithmName> {
        TODO("Not yet implemented")
    }
}