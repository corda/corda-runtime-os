package net.corda.crypto.impl

import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [CipherSuiteFactory::class])
open class CipherSuiteFactoryImpl : CipherSuiteFactory {
    companion object {
        private val logger: Logger = contextLogger()
    }

    @Reference(service = CipherSchemeMetadataProvider::class)
    lateinit var schemeMetadataProvider: CipherSchemeMetadataProvider

    @Reference(service = SignatureVerificationServiceProvider::class)
    lateinit var verifierProvider: SignatureVerificationServiceProvider

    @Reference(service = DigestServiceProvider::class)
    lateinit var digestServiceProvider: DigestServiceProvider

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
}