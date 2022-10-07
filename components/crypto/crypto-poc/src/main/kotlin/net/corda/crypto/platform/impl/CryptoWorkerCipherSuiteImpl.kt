package net.corda.crypto.platform.impl

import net.corda.v5.cipher.suite.CipherSuiteBase
import net.corda.v5.cipher.suite.CryptoWorkerCipherSuite
import net.corda.v5.cipher.suite.KeySchemeInfo
import net.corda.v5.cipher.suite.providers.CryptoWorkerCipherSuiteRegistrar
import net.corda.v5.cipher.suite.providers.encoding.KeyEncodingHandler
import net.corda.v5.cipher.suite.providers.generation.GenerateKeyHandler
import net.corda.v5.cipher.suite.providers.signing.SignDataHandler
import net.corda.v5.cipher.suite.providers.verification.VerifySignatureHandler
import net.corda.v5.cipher.suite.scheme.KeyScheme
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
        add(keyScheme)
        if (encodingHandler != null) {
            add(keyScheme.scheme, encodingHandler)
        }
        if (verifyHandler != null) {
            add(keyScheme.scheme, verifyHandler)
        }
        if(generateHandler != null) {
            add(keyScheme.scheme, generateHandler)
        }
        if(signHandler != null) {
            add(keyScheme.scheme, signHandler)
        }
    }

    override fun findGenerateKeyHandler(schemeCodeName: String): GenerateKeyHandler? =
        generateHandlers.getWithReadLock(schemeCodeName)

    override fun findSignDataHandler(schemeCodeName: String): SignDataHandler? =
        signHandlers.getWithReadLock(schemeCodeName)

    private fun add(keyScheme: KeyScheme, generateHandler: GenerateKeyHandler) = generateHandlers.withWriteLock {
        val existing = it[keyScheme.codeName]
        if (existing == null || existing.rank < generateHandler.rank) {
            it[keyScheme.codeName] = generateHandler
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                generateHandler::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                generateHandler.rank
            )
        }
    }

    private fun add(keyScheme: KeyScheme, signHandler: SignDataHandler) = signHandlers.withWriteLock {
        val existing = it[keyScheme.codeName]
        if (existing == null || existing.rank < signHandler.rank) {
            it[keyScheme.codeName] = signHandler
        } else {
            logger.warn(
                "Skipping adding {} because there is handler {} with higher rank than {}",
                signHandler::class.java.annotatedSuperclass,
                existing::class.java.annotatedSuperclass,
                signHandler.rank
            )
        }
    }
}