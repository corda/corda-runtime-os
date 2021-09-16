package net.corda.cipher.suite.impl

import net.corda.cipher.suite.impl.config.CipherSuiteConfig
import net.corda.cipher.suite.impl.config.CryptoConfigEvent
import net.corda.cipher.suite.impl.config.CryptoLibraryConfig
import net.corda.cipher.suite.impl.lifecycle.CryptoLifecycleComponent
import net.corda.cipher.suite.impl.lifecycle.CryptoServiceLifecycleEventHandler
import net.corda.cipher.suite.impl.lifecycle.clearCache
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

@Component(service = [CipherSuiteFactory::class])
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference(service = CryptoServiceLifecycleEventHandler::class)
    val cryptoServiceLifecycleEventHandler: CryptoServiceLifecycleEventHandler,
    @Reference(service = CipherSchemeMetadataProvider::class)
    private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,
    @Reference(service = SignatureVerificationServiceProvider::class)
    private val verifierProviders: List<SignatureVerificationServiceProvider>,
    @Reference(service = DigestServiceProvider::class)
    private val digestServiceProviders: List<DigestServiceProvider>
) : CryptoLifecycleComponent(cryptoServiceLifecycleEventHandler), CipherSuiteFactory {
    private var libraryConfig: CryptoLibraryConfig? = null

    private val isConfigured: Boolean
        get() = libraryConfig != null

    private val schemeMaps = ConcurrentHashMap<String, CipherSchemeMetadata>()
    private val verifiers = ConcurrentHashMap<String, SignatureVerificationService>()
    private val digestServices = ConcurrentHashMap<String, DigestService>()

    @Suppress("TooGenericExceptionCaught")
    override fun getSchemeMap(): CipherSchemeMetadata {
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
    override fun getCryptoService(info: CryptoServiceConfigInfo): CryptoService {
        // TODO2 - delete from the CipherSuiteFactory interface
        throw NotImplementedError()
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught", "MaxLineLength")
    override fun getSignatureVerificationService(): SignatureVerificationService {
        val name = getConfig().signatureVerificationProvider
        return verifiers.getOrPut(name) {
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
    override fun getDigestService(): DigestService {
        val name = getConfig().digestProvider
        return digestServices.getOrPut(name) {
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

    override fun stop() = lock.withLock {
        clearCaches()
        libraryConfig = null
        super.stop()
    }

    override fun handleLifecycleEvent(event: LifecycleEvent) = lock.withLock {
        logger.info("LifecycleEvent received: $event")
        when (event) {
            is CryptoConfigEvent -> {
                logger.info("Received config event {}", event::class.qualifiedName)
                reset(event.config)
            }
            is StopEvent -> {
                stop()
                logger.info("Received stop event")
            }
        }
    }

    private fun reset(config: CryptoLibraryConfig) {
        clearCaches()
        libraryConfig = config
    }

    private fun clearCaches() {
        schemeMaps.clearCache()
        verifiers.clearCache()
        digestServices.clearCache()
    }

    private fun getConfig(): CipherSuiteConfig {
        if (!isConfigured) {
            throw IllegalStateException("The factory is not configured.")
        }
        return libraryConfig!!.cipherSuite
    }
}