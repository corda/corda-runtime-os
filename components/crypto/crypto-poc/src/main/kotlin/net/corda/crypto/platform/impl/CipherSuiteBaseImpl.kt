package net.corda.crypto.platform.impl

import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.handlers.digest.DigestHandler
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.KeyScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.slf4j.LoggerFactory
import java.security.PublicKey
import java.security.SecureRandom

abstract class CipherSuiteBaseImpl : CipherSuiteBase {

    protected val logger = LoggerFactory.getLogger(this::class.java)

    private val keySchemeMap = KeySchemeMap()

    private val encodingHandlers = ReadWriteLockMap<String, KeyEncodingHandler>()

    private val verifyHandlers = ReadWriteLockMap<String, VerifySignatureHandler>()

    private val digestHandlers = ReadWriteLockMap<String, DigestHandler>()

    @Volatile
    private var _secureRandom: SecureRandom = SecureRandom()

    override val secureRandom: SecureRandom
        get() = _secureRandom

    protected fun add(keyScheme: KeySchemeInfo) =
        keySchemeMap.add(keyScheme)

    protected fun add(keyScheme: KeyScheme, encodingHandler: KeyEncodingHandler) = encodingHandlers.withWriteLock {
        val existing = it[keyScheme.codeName]
        if (existing == null || existing.rank < encodingHandler.rank) {
            it[keyScheme.codeName] = encodingHandler
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                encodingHandler::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                encodingHandler.rank
            )
        }
    }

    protected fun add(keyScheme: KeyScheme, verifyHandler: VerifySignatureHandler) = verifyHandlers.withWriteLock {
        val existing = it[keyScheme.codeName]
        if (existing == null || existing.rank < verifyHandler.rank) {
            it[keyScheme.codeName] = verifyHandler
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                verifyHandler::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                verifyHandler.rank
            )
        }
    }

    override fun register(secureRandom: SecureRandom) {
        _secureRandom = secureRandom
    }

    override fun register(algorithmName: String, digest: DigestHandler) = digestHandlers.withWriteLock {
        val existing = it[algorithmName]
        if (existing == null || existing.rank < digest.rank) {
            it[algorithmName] = digest
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                digest::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                digest.rank
            )
        }
    }

    override fun findKeyScheme(codeName: String): KeyScheme? =
        keySchemeMap.findKeyScheme(codeName)

    override fun findKeyScheme(publicKey: PublicKey): KeyScheme? =
        keySchemeMap.findKeyScheme(publicKey, getAllKeyEncodingHandlers())

    override fun findKeyScheme(algorithm: AlgorithmIdentifier): KeyScheme? =
        keySchemeMap.findKeyScheme(algorithm)

    override fun findDigestHandler(algorithmName: String): DigestHandler? =
        digestHandlers.getWithReadLock(algorithmName)

    override fun findVerifySignatureHandler(schemeCodeName: String): VerifySignatureHandler? =
        verifyHandlers.getWithReadLock(schemeCodeName)

    override fun findKeyEncodingHandler(schemeCodeName: String): KeyEncodingHandler? =
        encodingHandlers.getWithReadLock(schemeCodeName)

    override fun getAllKeyEncodingHandlers(): List<KeyEncodingHandler> =
        encodingHandlers.getAllValuesWithReadLock().distinctBy { it::class }
}