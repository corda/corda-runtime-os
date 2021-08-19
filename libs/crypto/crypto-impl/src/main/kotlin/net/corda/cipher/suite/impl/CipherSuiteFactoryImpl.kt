package net.corda.cipher.suite.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CipherSchemeMetadataProvider
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.cipher.suite.DigestServiceProvider
import net.corda.v5.cipher.suite.SignatureVerificationServiceProvider
import net.corda.v5.cipher.suite.config.CryptoServiceConfigInfo
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SignatureVerificationService
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
open class CipherSuiteFactoryImpl @Activate constructor(
    @Reference
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>,

    @Reference
    private val schemeMetadataProviders: List<CipherSchemeMetadataProvider>,

    @Reference
    private val verifierProviders: List<SignatureVerificationServiceProvider>,

    @Reference
    private val digestServiceProviders: List<DigestServiceProvider>
) : CipherSuiteFactory {

    private val objectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
        .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
        .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

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
    override fun getCryptoService(info: CryptoServiceConfigInfo): CryptoService {
        val provider = cryptoServiceProviders.firstOrNull { it.name == info.config.serviceName } as? CryptoServiceProvider<Any>
            ?: throw CryptoServiceLibraryException("Cannot find ${info.config.serviceName} implementing ${CryptoServiceProvider::class.java.name}")
        try {
            val context = CryptoServiceContext(
                category = info.category,
                sandboxId = info.effectiveSandboxId,
                cipherSuiteFactory = this,
                config = objectMapper.convertValue(info.config.serviceConfig, provider.configType)
            )
            return CryptoServiceCircuitBreaker(
                cryptoService = provider.getInstance(context),
                timeout = Duration.ofSeconds(15)
            )
        } catch (e: CryptoServiceLibraryException) {
            throw e
        } catch (e: Exception) {
            throw CryptoServiceLibraryException("Failed to create implementation of ${CryptoService::class.java.name}", e)
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