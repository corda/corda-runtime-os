package net.corda.crypto.service.impl.signing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.service.HSMRegistration
import net.corda.crypto.service.LifecycleNameProvider
import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.TenantHSMConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.CryptoService
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
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = HSMRegistration::class)
    private val hsmRegistrar: HSMRegistration,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemeMetadata: CipherSchemeMetadata,
    @Reference(
        service = CryptoServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
) : AbstractComponent<CryptoServiceFactoryImpl.Impl>(
    coordinatorFactory,
    LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
    InactiveImpl(),
    setOf(LifecycleCoordinatorName.forComponent<HSMRegistration>()) +
            cryptoServiceProviders.filterIsInstance(LifecycleNameProvider::class.java).map {
                it.lifecycleName
            }
), CryptoServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    interface Impl : AutoCloseable {
        fun getInstance(tenantId: String, category: String): CryptoServiceRef
        override fun close() = Unit
    }

    override fun createActiveImpl(): Impl = ActiveImpl(
        hsmRegistrar,
        schemeMetadata,
        cryptoServiceProviders
    )

    override fun createInactiveImpl(): Impl = InactiveImpl()

    override fun getInstance(tenantId: String, category: String): CryptoServiceRef =
        impl.getInstance(tenantId, category)

    internal class InactiveImpl : Impl {
        override fun getInstance(tenantId: String, category: String) =
            throw IllegalStateException("The component is in invalid state.")
    }

    internal class ActiveImpl(
        private val hsmRegistrar: HSMRegistration,
        private val schemeMetadata: CipherSchemeMetadata,
        cryptoServiceProviders: List<CryptoServiceProvider<*>>
    ) : Impl {
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

        private val cryptoServiceProvidersMap = cryptoServiceProviders.associateBy { it.name }

        private val cryptoServices = ConcurrentHashMap<String, CryptoServiceRef>()

        override fun getInstance(tenantId: String, category: String): CryptoServiceRef {
            val config = getConfig(tenantId, category)
            val key = config.tenant.hsmConfigId
            logger.debug(
                "Getting the crypto service  for hsmConfigId={} (tenantId={}, category={})",
                key, tenantId, category
            )
            return cryptoServices.computeIfAbsent(key) {
                val provider = findCryptoServiceProvider(config)
                try {
                    CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        signatureScheme = schemeMetadata.findSignatureScheme(
                            config.tenant.defaultScheme
                        ),
                        masterKeyAlias = config.tenant.wrappingKeyAlias,
                        aliasSecret = config.tenant.aliasSecret?.array(),
                        instance = createCryptoService(config, provider)
                    )
                } catch (e: Throwable) {
                    throw CryptoServiceLibraryException(
                        "Failed to create ${CryptoService::class.java.name} for $key",
                        e
                    )
                }
            }
        }

        private fun getConfig(tenantId: String, category: String): Config {
            val tenantConfig = hsmRegistrar.getTenantConfig(tenantId, category)
            return Config(
                hsmRegistrar.getHSMConfig(tenantConfig.hsmConfigId),
                tenantConfig
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun findCryptoServiceProvider(config: Config) =
            cryptoServiceProvidersMap[config.hsm.info.serviceName] as? CryptoServiceProvider<Any>
                ?: throw CryptoServiceLibraryException(
                    "Cannot find ${config.hsm.info.serviceName} for hsmConfigId=${config.tenant.hsmConfigId}",
                    isRecoverable = false
                )

        private fun createCryptoService(
            config: Config,
            provider: CryptoServiceProvider<Any>
        ): CryptoService {
            return CryptoServiceDecorator(
                cryptoService = provider.getInstance(
                    objectMapper.readValue(config.hsm.serviceConfig.array(), provider.configType)
                ),
                timeout = Duration.ofMillis(config.hsm.info.timeoutMills),
                retries = config.hsm.info.retries
            )
        }

        private data class Config(
            val hsm: HSMConfig,
            val tenant: TenantHSMConfig
        )
    }
}