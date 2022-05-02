package net.corda.crypto.service.impl.signing

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.LifecycleNameProvider
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
import net.corda.v5.crypto.exceptions.CryptoServiceException
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
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HSMService::class)
    private val hsmRegistrar: HSMService,
    @Reference(
        service = CryptoServiceProvider::class,
        cardinality = ReferenceCardinality.AT_LEAST_ONE,
        policyOption = ReferencePolicyOption.GREEDY
    )
    private val cryptoServiceProviders: List<CryptoServiceProvider<*>>
) : AbstractConfigurableComponent<CryptoServiceFactoryImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<CryptoServiceFactory>(),
    configurationReadService = configurationReadService,
    impl = InactiveImpl(),
    dependencies = setOf(LifecycleCoordinatorName.forComponent<HSMService>()) +
            cryptoServiceProviders.filterIsInstance(LifecycleNameProvider::class.java).map {
                it.lifecycleName
            },
    configKeys = setOf(
        ConfigKeys.MESSAGING_CONFIG,
        ConfigKeys.BOOT_CONFIG,
        ConfigKeys.CRYPTO_CONFIG
    )
), CryptoServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    interface Impl : AutoCloseable {
        fun getInstance(tenantId: String, category: String): CryptoServiceRef
        override fun close() = Unit
    }

    override fun createActiveImpl(event: ConfigChangedEvent): Impl = ActiveImpl(
        event,
        hsmRegistrar,
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
        event: ConfigChangedEvent,
        private val hsmRegistrar: HSMService,
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

        private val encryptor = event.config.toCryptoConfig().rootEncryptor()

        override fun getInstance(tenantId: String, category: String): CryptoServiceRef {
            val association = hsmRegistrar.getPrivateTenantAssociation(tenantId, category)
            val info = association.config.info
            val key = association.config.info.id
            logger.debug(
                "Getting the crypto service  for hsmConfigId={} (tenantId={}, category={})",
                key, tenantId, category
            )
            return cryptoServices.computeIfAbsent(key) {
                val provider = findCryptoServiceProvider(association.config.info)
                try {
                    CryptoServiceRef(
                        tenantId = tenantId,
                        category = category,
                        masterKeyAlias = info.masterKeyAlias,
                        aliasSecret = association.aliasSecret,
                        instance = createCryptoService(
                            info,
                            association.config.serviceConfig.array(),
                            provider
                        )
                    )
                } catch (e: Throwable) {
                    throw CryptoServiceException(
                        "Failed to create ${CryptoService::class.java.name} for $key",
                        e
                    )
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun findCryptoServiceProvider(info: HSMInfo) =
            cryptoServiceProvidersMap[info.serviceName] as? CryptoServiceProvider<Any>
                ?: throw CryptoServiceException("Cannot find ${info.serviceName}", isRecoverable = false)

        private fun createCryptoService(
            info: HSMInfo,
            serviceConfig: ByteArray,
            provider: CryptoServiceProvider<Any>
        ): CryptoService {
            return CryptoServiceDecorator(
                cryptoService = provider.getInstance(
                    objectMapper.readValue(
                        encryptor.decrypt(serviceConfig),
                        provider.configType
                    )
                ),
                timeout = Duration.ofMillis(info.timeoutMills),
                retries = info.retries
            )
        }
    }
}