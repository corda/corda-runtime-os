package net.corda.crypto.service.impl.signing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.service.CryptoServiceConfiguredInstance
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.impl.registration.HSMRegistration
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.TenantHSMConfig
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSuiteFactory
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceContext
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicyOption
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component(service = [CryptoServiceFactory::class])
class CryptoServiceFactoryImpl @Activate constructor(
    @Reference(service = HSMRegistration::class)
    private val hsmRegistrar: HSMRegistration,
    @Reference(service = CipherSuiteFactory::class)
    private val cipherSuiteFactory: CipherSuiteFactory,
    @Reference(
        service = CryptoServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
) : CryptoServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    private var impl: Impl = Impl(
        hsmRegistrar,
        cipherSuiteFactory,
        cryptoServiceProviders
    )

    override fun getInstance(tenantId: String, category: String): CryptoServiceConfiguredInstance =
        impl.getInstance(tenantId, category)

    private class Impl(
        private val hsmRegistrar: HSMRegistration,
        private val cipherSuiteFactory: CipherSuiteFactory,
        private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
    ) {
        companion object {
            private val jsonMapper = JsonMapper
                .builder()
                .enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)
                .build()
            private val objectMapper = jsonMapper
                .registerModule(JavaTimeModule())
                .registerModule(KotlinModule.Builder().build())
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        }

        private val cryptoServices = ConcurrentHashMap<Pair<String, String>, CryptoServiceConfiguredInstance>()

        fun getInstance(tenantId: String, category: String): CryptoServiceConfiguredInstance {
            val key = Pair(tenantId, category)
            logger.debug("Getting the crypto service  for {}", key)
            return cryptoServices.computeIfAbsent(key) {
                val config = getConfig(tenantId, category)
                val provider = findCryptoServiceProvider(config, key)
                try {
                    CryptoServiceConfiguredInstance(
                        tenantId = tenantId,
                        category = category,
                        defaultSignatureScheme = cipherSuiteFactory.getSchemeMap().findSignatureScheme(
                            config.tenant.defaultSignatureScheme
                        ),
                        wrappingKeyAlias = config.tenant.wrappingKeyAlias,
                        instance = createCryptoService(category, tenantId, config, provider)
                    )
                } catch (e: Throwable) {
                    throw CryptoServiceLibraryException(
                        "Failed to create ${CryptoService::class.java.name} for $key",
                        e
                    )
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun findCryptoServiceProvider(
            config: Config,
            key: Pair<String, String>
        ) = (cryptoServiceProviders.firstOrNull {
            it.name == config.hsm.info.serviceName
        } as? CryptoServiceProvider<Any>
            ?: throw CryptoServiceLibraryException(
                "Cannot find ${config.hsm.info.serviceName} for $key",
                isRecoverable = false
            ))

        private fun createCryptoService(
            category: String,
            tenantId: String,
            config: Config,
            provider: CryptoServiceProvider<Any>
        ): CryptoService {
            val context = CryptoServiceContext(
                category = category,
                memberId = tenantId,
                cipherSuiteFactory = cipherSuiteFactory,
                config = objectMapper.convertValue(config.hsm.serviceConfig, provider.configType)
            )
            return CryptoServiceDecorator(
                cryptoService = provider.getInstance(context),
                timeout = Duration.ofMillis(config.hsm.info.timeoutMills),
                retries = config.hsm.info.retries
            )
        }

        private fun getConfig(tenantId: String, category: String): Config {
            val tenantConfig = hsmRegistrar.getTenantConfig(tenantId, category)
            return Config(
                hsmRegistrar.getHSMConfig(tenantConfig.hsmConfigId),
                tenantConfig
            )
        }

        private data class Config(
            val hsm: HSMConfig,
            val tenant: TenantHSMConfig
        )
    }
}