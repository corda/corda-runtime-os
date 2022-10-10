package net.corda.crypto.service.cipher.suite.impl

import net.corda.crypto.core.ReadWriteLockMap
import net.corda.crypto.impl.cipher.suite.CipherSuiteBaseImpl
import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.KeyScheme
import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.handlers.CryptoWorkerCipherSuiteRegistrar
import net.corda.v5.cipher.suite.handlers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.handlers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.handlers.signing.SignDataHandler
import net.corda.v5.cipher.suite.handlers.verification.VerifySignatureHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Component(service = [CryptoWorkerCipherSuite::class, CipherSuiteBase::class])
class CryptoWorkerCipherSuiteImpl @Activate constructor(
    @Reference(
        service = CryptoWorkerCipherSuiteRegistrar::class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    registrars: List<CryptoWorkerCipherSuiteRegistrar>
) : CipherSuiteBaseImpl(), CryptoWorkerCipherSuite {

    private val generateHandlers = ReadWriteLockMap<String, GenerateKeyHandler>()

    private val signHandlers = ReadWriteLockMap<String, SignDataHandler>()

    init {
        registrars.forEach {
            it.registerWith(this)
        }
    }

    override fun register(
        keyScheme: KeySchemeInfo,
        encodingHandler: KeyEncodingHandler?,
        verifyHandler: VerifySignatureHandler?,
        generateHandler: GenerateKeyHandler?,
        signHandler: SignDataHandler?
    ) {
        if (encodingHandler == null && verifyHandler == null && generateHandler == null && signHandler == null) {
            return
        }
        var anyAdded = false
        if (encodingHandler != null && add(keyScheme.scheme, encodingHandler)) {
            anyAdded = true
        }
        if (verifyHandler != null && add(keyScheme.scheme, verifyHandler)) {
            anyAdded = true
        }
        if (generateHandler != null && add(keyScheme.scheme, generateHandler)) {
            anyAdded = true
        }
        if (signHandler != null && add(keyScheme.scheme, signHandler)) {
            anyAdded = true
        }
        if (anyAdded) {
            add(keyScheme)
        }
    }

    override fun findGenerateKeyHandler(schemeCodeName: String): GenerateKeyHandler? =
        generateHandlers.getWithReadLock(schemeCodeName)

    override fun findSignDataHandler(schemeCodeName: String): SignDataHandler? =
        signHandlers.getWithReadLock(schemeCodeName)

    private fun add(keyScheme: KeyScheme, generateHandler: GenerateKeyHandler): Boolean =
        generateHandlers.withWriteLock {
            val existing = it[keyScheme.codeName]
            if (existing == null || existing.rank < generateHandler.rank) {
                it[keyScheme.codeName] = generateHandler
                true
            } else {
                logger.warn(
                    "Skipping adding {} because there is handler {} with higher rank than {}",
                    generateHandler::class.java.annotatedSuperclass,
                    existing::class.java.annotatedSuperclass,
                    generateHandler.rank
                )
                false
            }
        }

    private fun add(keyScheme: KeyScheme, signHandler: SignDataHandler): Boolean = signHandlers.withWriteLock {
        val existing = it[keyScheme.codeName]
        if (existing == null || existing.rank < signHandler.rank) {
            it[keyScheme.codeName] = signHandler
            true
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                signHandler::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                signHandler.rank
            )
            false
        }
    }
}