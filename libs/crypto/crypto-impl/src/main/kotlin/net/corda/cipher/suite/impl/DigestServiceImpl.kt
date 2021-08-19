package net.corda.cipher.suite.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestAlgorithm
import net.corda.v5.cipher.suite.DigestAlgorithmFactory
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import java.util.concurrent.ConcurrentHashMap

class DigestServiceImpl(
    private val schemeMetadata: CipherSchemeMetadata,
    private val customDigestAlgorithmFactories: List<DigestAlgorithmFactory>
) : DigestService {
    private val factories = ConcurrentHashMap<String, DigestAlgorithmFactory>().also { factories ->
        customDigestAlgorithmFactories.forEach { factory ->
            factories[factory.algorithm] = factory
        }
    }
    private val lengths = ConcurrentHashMap<String, Int>()

    override fun hash(bytes: ByteArray, digestAlgorithmName: DigestAlgorithmName): SecureHash {
        val hashBytes = digestFor(digestAlgorithmName).digest(bytes)
        return SecureHash(digestAlgorithmName.name, hashBytes)
    }

    override fun digestLength(digestAlgorithmName: DigestAlgorithmName): Int = lengths.getOrPut(digestAlgorithmName.name) {
        return digestFor(digestAlgorithmName).digestLength
    }

    private fun digestFor(digestAlgorithmName: DigestAlgorithmName): DigestAlgorithm = factories.getOrPut(digestAlgorithmName.name) {
        SpiDigestAlgorithmFactory(schemeMetadata, digestAlgorithmName.name)
    }.getInstance()
}