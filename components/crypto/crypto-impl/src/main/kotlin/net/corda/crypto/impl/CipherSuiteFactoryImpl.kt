package net.corda.crypto.impl

import net.corda.crypto.impl.config.CipherSuiteConfig
import net.corda.crypto.impl.config.cipherSuite
import net.corda.crypto.impl.lifecycle.clearCache
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component(service = [CipherSuiteFactory::class])
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadataProvider::class)
    private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,
    @Reference(service = SignatureVerificationServiceProvider::class)
    private val verifierProviders: List<SignatureVerificationServiceProvider>,
    @Reference(service = DigestServiceProvider::class)
    private val digestServiceProviders: List<DigestServiceProvider>
) : Lifecycle, CryptoLifecycleComponent, CipherSuiteFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lock = ReentrantLock()

    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private val schemeMaps = HashMap<String, CipherSchemeMetadata>()

    private val verifiers = HashMap<String, SignatureVerificationService>()

    private val digestServices = HashMap<String, DigestService>()

    override var isRunning: Boolean = false

    override fun start() = lock.withLock {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() = lock.withLock {
        logger.info("Stopping...")
        clearCaches()
        libraryConfig = null
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) = lock.withLock {
        logger.info("Received new configuration...")
        clearCaches()
        libraryConfig = config
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getSchemeMap(): CipherSchemeMetadata = lock.withLock  {
        val name = getConfig().schemeMetadataProvider
        return schemeMaps.getOrPut(name) {
            val provider = schemeMetadataProviders.firstOrNull { it.name == name }
                ?: throw CryptoServiceLibraryException("Cannot find $name implementing ${CipherSchemeMetadataProvider::class.java.name}")
            try {
                provider.getInstance()
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to create implementation of ${CipherSchemeMetadata::class.java.name}", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught", "MaxLineLength")
    override fun getSignatureVerificationService(): SignatureVerificationService = lock.withLock  {
        val name = getConfig().signatureVerificationProvider
        verifiers.getOrPut(name) {
            val provider = verifierProviders.firstOrNull { it.name == name }
                ?: throw CryptoServiceLibraryException("Cannot find $name implementing ${SignatureVerificationServiceProvider::class.java.name}")
            try {
                provider.getInstance(this)
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException(
                    "Failed to create implementation of ${SignatureVerificationService::class.java.name}",
                    e
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getDigestService(): DigestService = lock.withLock  {
        val name = getConfig().digestProvider
        digestServices.getOrPut(name) {
            val provider = digestServiceProviders.firstOrNull { it.name == name }
                ?: throw CryptoServiceLibraryException("Cannot find $name implementing ${DigestServiceProvider::class.java.name}")
            try {
                provider.getInstance(this)
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Throwable) {
                throw CryptoServiceLibraryException("Failed to create implementation of ${DigestService::class.java.name}", e)
            }
        }
    }

    private fun clearCaches() {
        schemeMaps.clearCache()
        verifiers.clearCache()
        digestServices.clearCache()
    }

    private fun getConfig(): CipherSuiteConfig {
        return if (!isConfigured) {
            logger.warn("The factory is not yet configured, ...using the default values.")
            CipherSuiteConfig(emptyMap())
        } else {
            libraryConfig!!.cipherSuite
        }
    }
}