package net.corda.crypto.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.CRYPTO_TENANT_ID
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.PrivateKeyPolicy
import net.corda.crypto.config.impl.hsmService
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.impl.retrying.BackoffStrategy
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.service.HSMService
import net.corda.crypto.service.SigningServiceFactory
import net.corda.crypto.softhsm.CryptoRepository
import net.corda.crypto.softhsm.cryptoRepositoryFactory
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.JpaEntitiesRegistry
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.utilities.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("LongParameterList")
@Component(service = [HSMService::class])
class HSMServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = JpaEntitiesRegistry::class)
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = PlatformDigestService::class)
    private val digestService: PlatformDigestService,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
    @Reference(service = SigningServiceFactory::class)
    private val signingServiceFactory: SigningServiceFactory
) : AbstractConfigurableComponent<HSMServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMService>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        )
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), HSMService {
    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(
            logger,
            event,
            cryptoRepositoryFactory(
                CryptoTenants.CRYPTO,
                event.config.toCryptoConfig(),
                dbConnectionManager,
                jpaEntitiesRegistry,
                virtualNodeInfoReadService,
                keyEncodingService,
                digestService,
                layeredPropertyMapFactory,
            ),
            signingServiceFactory
        )

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo =
        impl.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo =
        impl.assignSoftHSM(tenantId, category)

    override fun findAssignedHSM(tenantId: String, category: String): HSMAssociationInfo? =
        impl.findAssignedHSM(tenantId, category)

    class Impl(
        private val logger: Logger,
        event: ConfigChangedEvent,
        private val cryptoRepository: CryptoRepository,
        private val signingServiceFactory: SigningServiceFactory
    ) : DownstreamAlwaysUpAbstractImpl() {
        companion object {
            private fun Map<String, String>.isPreferredPrivateKeyPolicy(policy: String): Boolean =
                this[CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY] == policy
        }

        private val config = event.config.toCryptoConfig()

        private val hsmConfig = config.hsmService()

        private val hsmMap = HSMMap(config, cryptoRepository)

        private val executor = CryptoRetryingExecutor(
            logger,
            BackoffStrategy.createBackoff(hsmConfig.downstreamMaxAttempts, listOf(100L))
        )

        fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo {
            logger.info("assignHSM(tenant={}, category={})", tenantId, category)
            if(hsmMap.isOnlySoftHSM) {
                logger.warn("There is only SOFT HSM configured, will assign that.")
                return assignSoftHSM(tenantId, category)
            }
            val existing = cryptoRepository.findTenantAssociation(tenantId, category)
            if(existing != null) {
                logger.warn(
                    "The ${existing.hsmId} HSM already assigned for tenant={}, category={}",
                    tenantId,
                    category)
                ensureWrappingKey(existing)
                return existing
            }
            val stats = hsmMap.getHSMStats(category).filter { s -> s.allUsages < s.capacity }
            val chosen = if (context.isPreferredPrivateKeyPolicy(CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_ALIASED)) {
                tryChooseAliased(stats)
            } else {
                tryChooseAny(stats)
            }
            val association = cryptoRepository.associate(
                tenantId = tenantId,
                category = category,
                hsmId = chosen.hsmId,
                masterKeyPolicy = hsmMap.getMasterKeyPolicy(chosen.hsmId)
            )
            ensureWrappingKey(association)
            return association
        }

        fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo {
            logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
            val existing = cryptoRepository.findTenantAssociation(tenantId, category)
            if(existing != null) {
                logger.warn(
                    "The ${existing.hsmId} HSM already assigned for tenant={}, category={}",
                    tenantId,
                    category)
                ensureWrappingKey(existing)
                return existing
            }
            val association = cryptoRepository.associate(
                tenantId = tenantId,
                category = category,
                hsmId = SOFT_HSM_ID,
                masterKeyPolicy = hsmMap.getMasterKeyPolicy(SOFT_HSM_ID)
            )
            ensureWrappingKey(association)
            return association
        }

        fun findAssignedHSM(tenantId: String, category: String): HSMAssociationInfo? {
            logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
            return cryptoRepository.findTenantAssociation(tenantId, category)
        }

        private fun ensureWrappingKey(association: HSMAssociationInfo) {
            if (hsmMap.getMasterKeyPolicy(association.hsmId) == MasterKeyPolicy.UNIQUE) {
                require(!association.masterKeyAlias.isNullOrBlank()) {
                    "The master key alias is not specified."
                }

                val signingService = signingServiceFactory.getInstance()
                signingService
                    .createWrappingKey(
                        hsmId = association.hsmId,
                        failIfExists = false,
                        masterKeyAlias = association.masterKeyAlias!!,
                        context = mapOf(
                            CRYPTO_TENANT_ID to association.tenantId
                        )
                )
            }
        }

        private fun tryChooseAliased(stats: List<HSMStats>): HSMStats =
            stats.filter {
                it.privateKeyPolicy == PrivateKeyPolicy.ALIASED ||
                        it.privateKeyPolicy == PrivateKeyPolicy.BOTH
            }.minByOrNull { s ->
                s.allUsages
            } ?: tryChooseAny(stats)

        private fun tryChooseAny(stats: List<HSMStats>): HSMStats =
            stats.minByOrNull { s ->
                s.allUsages
            } ?: throw IllegalStateException("There is no available HSMs.")
    }
}