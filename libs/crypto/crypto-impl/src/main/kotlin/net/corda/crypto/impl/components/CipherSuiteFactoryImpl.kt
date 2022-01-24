package net.corda.crypto.impl.components

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
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
import org.slf4j.Logger

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
@Component(service = [CipherSuiteFactory::class])
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadataProvider::class)
    private val schemeMetadataProvider: CipherSchemeMetadataProvider,
    @Reference(service = SignatureVerificationServiceProvider::class)
    private val verifierProvider: SignatureVerificationServiceProvider,
    @Reference(service = DigestServiceProvider::class)
    private val digestServiceProvider: DigestServiceProvider,
    context: BundleContext
) : CipherSuiteFactory {
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

    private val _schemeMap: CipherSchemeMetadata by lazy(LazyThreadSafetyMode.PUBLICATION) {
        schemeMetadataProvider.getInstance()
    }

    private val _verifier: SignatureVerificationService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        verifierProvider.getInstance(this)
    }

    private val _digestService: DigestService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        digestServiceProvider.getInstance(this)
    }

    override fun getSchemeMap(): CipherSchemeMetadata {
        logger.debug("Getting {}", CipherSchemeMetadata::class.java.name)
        return try {
            _schemeMap
        } catch (e: CryptoServiceLibraryException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException(
                "Failed to get instance of ${CipherSchemeMetadata::class.java.name}",
                e
            )
        }
    }

    override fun getSignatureVerificationService(): SignatureVerificationService {
        logger.debug("Getting {}", SignatureVerificationService::class.java.name)
        return try {
            _verifier
        } catch (e: CryptoServiceLibraryException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException(
                "Failed to get instance of ${SignatureVerificationService::class.java.name}",
                e
            )
        }
    }

    override fun getDigestService(): DigestService {
        logger.debug("Getting {}", DigestService::class.java.name)
        return try {
            _digestService
        } catch (e: CryptoServiceLibraryException) {
            throw e
        } catch (e: Throwable) {
            throw CryptoServiceLibraryException(
                "Failed to get instance implementation of ${DigestService::class.java.name}",
                e
            )
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