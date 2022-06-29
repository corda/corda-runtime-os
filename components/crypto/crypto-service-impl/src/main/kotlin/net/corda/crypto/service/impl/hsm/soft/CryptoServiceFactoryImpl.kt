package net.corda.crypto.service.impl.hsm.soft

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.impl.config.rootEncryptor
import net.corda.crypto.impl.config.toCryptoConfig
import net.corda.crypto.impl.decorators.CryptoServiceDecorator
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.CryptoServiceFactory
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.LifecycleNameProvider
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CryptoService
import net.corda.v5.cipher.suite.CryptoServiceProvider
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
        MESSAGING_CONFIG,
        BOOT_CONFIG,
        CRYPTO_CONFIG
    )
), CryptoServiceFactory {
    companion object {
        private val logger = contextLogger()
    }

    interface Impl : AutoCloseable {
        fun getInstance(tenantId: String, category: String): CryptoServiceRef
        fun getInstance(configId: String): CryptoService
        fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef
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

    override fun getInstance(configId: String): CryptoService =
        impl.getInstance(configId)

    override fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef =
        impl.getInstance(tenantId, category, associationId)

    internal class InactiveImpl : Impl {
        override fun getInstance(tenantId: String, category: String) =
            throw IllegalStateException("The component is in invalid state.")

        override fun getInstance(configId: String) =
            throw IllegalStateException("The component is in invalid state.")

        override fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef =
            throw IllegalStateException("The component is in invalid state.")
    }

    internal class ActiveImpl(
        event: ConfigChangedEvent,
        private val hsmRegistrar: HSMService,
        cryptoServiceProviders: List<CryptoServiceProvider<*>>
    ) : Impl {
        private val cryptoServiceProvidersMap = cryptoServiceProviders.associateBy { it.name }

        private val cryptoServices = ConcurrentHashMap<String, CryptoService>()

        private val cryptoAssociations = ConcurrentHashMap<String, CryptoServiceRef>()

        private val cryptoRefs = ConcurrentHashMap<Pair<String, String>, CryptoServiceRef>()

        private val encryptor = event.config.toCryptoConfig().rootEncryptor()

        override fun getInstance(tenantId: String, category: String): CryptoServiceRef {
            logger.debug {
                "Getting the crypto service for tenantId=$tenantId, category=$category)"
            }
            return cryptoRefs.computeIfAbsent(tenantId to category) {
                val association = hsmRegistrar.findAssignedHSM(tenantId, category)
                    ?: throw IllegalStateException("The tenant=$tenantId is not configured for category=$category")
                createCryptoServiceRef(association)
            }
        }

        override fun getInstance(tenantId: String, category: String, associationId: String): CryptoServiceRef {
            logger.debug {
                "Getting the crypto service for tenantId=$tenantId, category=$category, associationId=$associationId)"
            }
            return cryptoAssociations.computeIfAbsent(associationId) {
                val association = hsmRegistrar.findAssignedHSM(tenantId, category)
                    ?: throw IllegalStateException("The tenant=$tenantId is not configured for category=$category")
                require(association.tenantId == tenantId && association.category == category) {
                    "The association $associationId is not for tenant=$tenantId and category=$category"
                }
                createCryptoServiceRef(association)
            }.also {
                require(it.tenantId == tenantId && it.category == category) {
                    "The association $associationId is not for tenant=$tenantId and category=$category"
                }
            }
        }

        override fun getInstance(configId: String): CryptoService {
            logger.debug { "Getting the crypto service for configId=$configId)" }
            val config = hsmRegistrar.findHSMConfig(configId)
                ?: throw IllegalStateException("The config=$configId is not found.")
            return getInstance(config.info, config.serviceConfig)
        }

        private fun getInstance(info: HSMInfo, serviceConfig: ByteArray): CryptoService =
            cryptoServices.computeIfAbsent(info.id) {
                val provider = findCryptoServiceProvider(info.serviceName)
                CryptoServiceDecorator.create(
                    provider,
                    encryptor.decrypt(serviceConfig),
                    info.maxAttempts,
                    Duration.ofMillis(info.attemptTimeoutMills)
                )
            }

        @Suppress("UNCHECKED_CAST")
        private fun findCryptoServiceProvider(serviceName: String) =
            cryptoServiceProvidersMap[serviceName] as? CryptoServiceProvider<Any>
                ?: throw IllegalStateException("Cannot find $serviceName")

        private fun createCryptoServiceRef(association: HSMTenantAssociation): CryptoServiceRef {
            logger.info(
                "Creating {}: id={} configId={} (tenantId={}, category={})",
                CryptoServiceRef::class.simpleName,
                association.id,
                association.config.info.id,
                association.tenantId,
                association.category
            )
            return CryptoServiceRef(
                tenantId = association.tenantId,
                category = association.category,
                masterKeyAlias = association.masterKeyAlias,
                aliasSecret = association.aliasSecret,
                associationId = association.id,
                instance = getInstance(association.config.info, association.config.serviceConfig)
            )
        }
    }
}