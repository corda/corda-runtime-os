package net.corda.crypto.impl

import net.corda.crypto.impl.config.CipherSuiteConfig
import net.corda.crypto.impl.config.cipherSuite
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.lifecycle.CryptoLifecycleComponent
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.annotation.bundle.Capabilities
import org.osgi.annotation.bundle.Capability
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceFactory
import org.osgi.framework.ServiceRegistration
import org.osgi.namespace.service.ServiceNamespace.CAPABILITY_OBJECTCLASS_ATTRIBUTE
import org.osgi.namespace.service.ServiceNamespace.SERVICE_NAMESPACE
import org.osgi.resource.Namespace.EFFECTIVE_ACTIVE
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

@Capabilities(
    Capability(
        namespace = SERVICE_NAMESPACE,
        attribute = ["${CAPABILITY_OBJECTCLASS_ATTRIBUTE}:List<String>=net.corda.v5.crypto.DigestService"],
        uses = [ DigestService::class ],
        effective = EFFECTIVE_ACTIVE
    ),
    Capability(
        namespace = SERVICE_NAMESPACE,
        attribute = ["${CAPABILITY_OBJECTCLASS_ATTRIBUTE}:List<String>=net.corda.v5.cipher.suite.CipherSchemeMetadata"],
        uses = [ CipherSchemeMetadata::class ],
        effective = EFFECTIVE_ACTIVE
    ),
    Capability(
        namespace = SERVICE_NAMESPACE,
        attribute = ["${CAPABILITY_OBJECTCLASS_ATTRIBUTE}:List<String>=net.corda.v5.cipher.suite.KeyEncodingService"],
        uses = [ KeyEncodingService::class ],
        effective = EFFECTIVE_ACTIVE
    ),
    Capability(
        namespace = SERVICE_NAMESPACE,
        attribute = ["${CAPABILITY_OBJECTCLASS_ATTRIBUTE}:List<String>=net.corda.v5.cipher.suite.SignatureVerificationServiceProvider"],
        uses = [ SignatureVerificationService::class ],
        effective = EFFECTIVE_ACTIVE
    )
)
@Component(immediate = true, service = [ CipherSuiteFactory::class ])
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference(
        service = CipherSchemeMetadataProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,
    @Reference(
        service = SignatureVerificationServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val verifierProviders: List<SignatureVerificationServiceProvider>,
    @Reference(
        service = DigestServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val digestServiceProviders: List<DigestServiceProvider>,
    context: BundleContext
) : Lifecycle, CryptoLifecycleComponent, CipherSuiteFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val signatureVerificationRegistration = context.registerService(
        arrayOf(SignatureVerificationService::class.java.name),
        SignatureVerificationFactory(),
        null
    )

    private val schemeMetadataRegistration = context.registerService(
        arrayOf(CipherSchemeMetadata::class.java.name, KeyEncodingService::class.java.name),
        CipherSchemeMetadataFactory(),
        null
    )

    private val digestServiceRegistration = context.registerService(
        arrayOf(DigestService::class.java.name),
        DigestServiceFactory(),
        null
    )

    @Suppress("unused")
    @Deactivate
    fun done() {
        signatureVerificationRegistration.unregister()
        schemeMetadataRegistration.unregister()
        digestServiceRegistration.unregister()
    }

    private var impl = Impl(
        schemeMetadataProviders,
        verifierProviders,
        digestServiceProviders,
        CipherSuiteConfig(emptyMap()),
        logger
    )

    override var isRunning: Boolean = false

    override fun start() {
        logger.info("Starting...")
        isRunning = true
    }

    override fun stop() {
        logger.info("Stopping...")
        impl.closeGracefully()
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
        currentImpl.closeGracefully()
    }

    override fun getSchemeMap(): CipherSchemeMetadata =
        impl.getSchemeMap()

    override fun getSignatureVerificationService(): SignatureVerificationService =
        impl.getSignatureVerificationService()

    override fun getDigestService(): DigestService =
        impl.getDigestService()

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

        override fun getSchemeMap(): CipherSchemeMetadata  {
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
        override fun getSignatureVerificationService(): SignatureVerificationService  {
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
                    throw CryptoServiceLibraryException("Failed to create implementation of ${DigestService::class.java.name}", e)
                }
            }
        }

        override fun close() {
            schemeMaps.clearCache()
            verifiers.clearCache()
            digestServices.clearCache()
        }
    }

    private inner class SignatureVerificationFactory : ServiceFactory<SignatureVerificationService> {
        override fun getService(
            bundle: Bundle?,
            registration: ServiceRegistration<SignatureVerificationService>?
        ): SignatureVerificationService {
            return getSignatureVerificationService()
        }

        override fun ungetService(
            bundle: Bundle?,
            registration: ServiceRegistration<SignatureVerificationService>?,
            service: SignatureVerificationService?
        ) {}
    }

    private inner class CipherSchemeMetadataFactory : ServiceFactory<CipherSchemeMetadata> {
        override fun getService(
            bundle: Bundle?,
            registration: ServiceRegistration<CipherSchemeMetadata>?
        ): CipherSchemeMetadata {
            return getSchemeMap()
        }

        override fun ungetService(
            bundle: Bundle?,
            registration: ServiceRegistration<CipherSchemeMetadata>?,
            service: CipherSchemeMetadata?
        ) {}
    }

    private inner class DigestServiceFactory : ServiceFactory<DigestService> {
        override fun getService(bundle: Bundle?, registration: ServiceRegistration<DigestService>?): DigestService {
            return getDigestService()
        }

        override fun ungetService(
            bundle: Bundle?,
            registration: ServiceRegistration<DigestService>?,
            service: DigestService?
        ) {}
    }
}
