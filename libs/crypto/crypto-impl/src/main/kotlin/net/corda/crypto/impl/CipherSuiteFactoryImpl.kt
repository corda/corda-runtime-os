package net.corda.crypto.impl

import net.corda.crypto.impl.config.CipherSuiteConfig
import net.corda.crypto.impl.config.cipherSuite
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
import net.corda.v5.crypto.exceptions.CryptoServiceException
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CipherSuiteFactory::class])
open class CipherSuiteFactoryImpl : Lifecycle, CryptoLifecycleComponent, CipherSuiteFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private lateinit var schemeMetadataProviders: List<CipherSchemeMetadataProvider>
    private lateinit var verifierProviders: List<SignatureVerificationServiceProvider>
    private lateinit var digestServiceProviders: List<DigestServiceProvider>

    @Activate
    fun activate(
        @Reference(
            service = CipherSchemeMetadataProvider::class,
            cardinality = ReferenceCardinality.AT_LEAST_ONE,
            policyOption = ReferencePolicyOption.GREEDY
        )
        schemeMetadataProviders: List<CipherSchemeMetadataProvider>,
        @Reference(
            service = SignatureVerificationServiceProvider::class,
            cardinality = ReferenceCardinality.AT_LEAST_ONE,
            policyOption = ReferencePolicyOption.GREEDY
        )
        verifierProviders: List<SignatureVerificationServiceProvider>,
        @Reference(
            service = DigestServiceProvider::class,
            cardinality = ReferenceCardinality.AT_LEAST_ONE,
            policyOption = ReferencePolicyOption.GREEDY
        )
        digestServiceProviders: List<DigestServiceProvider>
    ) {
        this.schemeMetadataProviders = schemeMetadataProviders
        this.verifierProviders = verifierProviders
        this.digestServiceProviders = digestServiceProviders
    }

    private var impl: Impl? = null

    private fun impl(): Impl =
        impl ?: throw CryptoServiceException("Factory have not been initialised yet.", true)

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl?.closeGracefully()
        isRunning = false
    }

    override fun handleConfigEvent(config: CryptoLibraryConfig) {
        logger.info("Received new configuration...")
        val currentImpl = impl
        impl = Impl(
            schemeMetadataProviders,
            verifierProviders,
            digestServiceProviders,
            config.cipherSuite,
            logger
        )
        currentImpl?.closeGracefully()
    }

    override fun getSchemeMap(): CipherSchemeMetadata =
        impl().getSchemeMap()

    override fun getSignatureVerificationService(): SignatureVerificationService =
        impl().getSignatureVerificationService()

    override fun getDigestService(): DigestService =
        impl().getDigestService()

    private class Impl(
        private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,
        private val verifierProviders: List<SignatureVerificationServiceProvider>,
        private val digestServiceProviders: List<DigestServiceProvider>,
        private val config: CipherSuiteConfig,
        private val logger: Logger
    ) : CipherSuiteFactory, AutoCloseable {
        private val schemeMaps = ConcurrentHashMap<String, CipherSchemeMetadata>()
        private val verifiers = ConcurrentHashMap<String, SignatureVerificationService>()
        private val digestServices = ConcurrentHashMap<String, DigestService>()

        override fun getSchemeMap(): CipherSchemeMetadata {
            logger.debug("Getting {}", CipherSchemeMetadata::class.java.name)
            val name = config.schemeMetadataProvider
            return schemeMaps.getOrPut(name) {
                logger.info("Creating {}", CipherSchemeMetadata::class.java.name)
                val provider = schemeMetadataProviders.firstOrNull { it.name == name }
                    ?: throw CryptoServiceLibraryException(
                        "Cannot find $name implementing ${CipherSchemeMetadataProvider::class.java.name}"
                    )
                try {
                    provider.getInstance()
                } catch (e: CryptoServiceLibraryException) {
                    throw e
                } catch (e: Throwable) {
                    throw CryptoServiceLibraryException(
                        "Failed to create implementation of ${CipherSchemeMetadata::class.java.name}",
                        e
                    )
                }
            }
        }

        @Suppress("UNCHECKED_CAST", "MaxLineLength")
        override fun getSignatureVerificationService(): SignatureVerificationService {
            logger.debug("Getting {}", SignatureVerificationService::class.java.name)
            val name = config.signatureVerificationProvider
            return verifiers.getOrPut(name) {
                logger.info("Creating {}", SignatureVerificationService::class.java.name)
                val provider = verifierProviders.firstOrNull { it.name == name }
                    ?: throw CryptoServiceLibraryException(
                        "Cannot find $name implementing ${SignatureVerificationServiceProvider::class.java.name}"
                    )
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

        override fun getDigestService(): DigestService {
            logger.debug("Getting {}", DigestService::class.java.name)
            val name = config.digestProvider
            return digestServices.getOrPut(name) {
                logger.info("Creating {}", DigestService::class.java.name)
                val provider = digestServiceProviders.firstOrNull { it.name == name }
                    ?: throw CryptoServiceLibraryException("Cannot find $name implementing ${DigestServiceProvider::class.java.name}")
                try {
                    provider.getInstance(this)
                } catch (e: CryptoServiceLibraryException) {
                    throw e
                } catch (e: Throwable) {
                    throw CryptoServiceLibraryException(
                        "Failed to create implementation of ${DigestService::class.java.name}",
                        e
                    )
                }
            }
        }

        override fun close() {
            schemeMaps.clearCache()
            verifiers.clearCache()
            digestServices.clearCache()
        }
    }
}