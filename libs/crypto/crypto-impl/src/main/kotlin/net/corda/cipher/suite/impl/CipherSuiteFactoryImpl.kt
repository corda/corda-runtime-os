package net.corda.cipher.suite.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap

@Component
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference
    private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,

    @Reference
    private val verifierProviders: List<SignatureVerificationServiceProvider>,

    @Reference
    private val digestServiceProviders: List<DigestServiceProvider>
) : CipherSuiteFactory {

    private val schemeMaps = ConcurrentHashMap<String, CipherSchemeMetadata>()
    private val verifiers = ConcurrentHashMap<String, SignatureVerificationService>()
    private val digestServices = ConcurrentHashMap<String, DigestService>()

    @Suppress("TooGenericExceptionCaught")
    override fun getSchemeMap(): CipherSchemeMetadata {
        val tmpName = "default"
        return schemeMaps.getOrPut(tmpName) {
            val provider = schemeMetadataProviders.firstOrNull { it.name == tmpName }
                ?: throw CryptoServiceLibraryException("Cannot find $tmpName implementing ${CipherSchemeMetadataProvider::class.java.name}")
            try {
                provider.getInstance()
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceLibraryException("Failed to create implementation of ${CipherSchemeMetadata::class.java.name}", e)
            }
        }
    }

    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught", "MaxLineLength")
    override fun getSignatureVerificationService(): SignatureVerificationService {
        val tmpName = "default"
        return verifiers.getOrPut(tmpName) {
            val provider = verifierProviders.firstOrNull { it.name == tmpName }
                ?: throw CryptoServiceLibraryException("Cannot find $tmpName implementing ${SignatureVerificationServiceProvider::class.java.name}")
            try {
                provider.getInstance(this)
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceLibraryException(
                    "Failed to create implementation of ${SignatureVerificationService::class.java.name}",
                    e
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun getDigestService(): DigestService {
        val tmpName = "default"
        return digestServices.getOrPut(tmpName) {
            val provider = digestServiceProviders.firstOrNull { it.name == tmpName }
                ?: throw CryptoServiceLibraryException("Cannot find $tmpName implementing ${DigestServiceProvider::class.java.name}")
            try {
                provider.getInstance(this)
            } catch (e: CryptoServiceLibraryException) {
                throw e
            } catch (e: Exception) {
                throw CryptoServiceLibraryException("Failed to create implementation of ${DigestService::class.java.name}", e)
            }
        }
    }
}