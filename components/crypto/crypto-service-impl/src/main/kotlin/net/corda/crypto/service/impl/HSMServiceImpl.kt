package net.corda.crypto.service.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.component.impl.AbstractConfigurableComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.PrivateKeyPolicy
import net.corda.crypto.config.impl.hsmService
import net.corda.crypto.config.impl.toCryptoConfig
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_WORKER_SET_ID
import net.corda.crypto.impl.retrying.CryptoRetryingExecutor
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.service.HSMService
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.schema.configuration.ConfigKeys.CRYPTO_CONFIG
import net.corda.v5.base.exceptions.BackoffStrategy
import net.corda.v5.base.util.debug
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [HSMService::class])
class HSMServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = HSMStore::class)
    private val store: HSMStore,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : AbstractConfigurableComponent<HSMServiceImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMService>(),
    configurationReadService = configurationReadService,
    upstream = DependenciesTracker.Default(
        setOf(
            LifecycleCoordinatorName.forComponent<HSMStore>(),
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
        )
    ),
    configKeys = setOf(CRYPTO_CONFIG)
), HSMService {
    override fun createActiveImpl(event: ConfigChangedEvent): Impl =
        Impl(logger, event, store, cryptoOpsClient)

    override fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo =
        impl.assignHSM(tenantId, category, context)

    override fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo =
        impl.assignSoftHSM(tenantId, category)

    override fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? =
        impl.findAssignedHSM(tenantId, category)

    class Impl(
        private val logger: Logger,
        event: ConfigChangedEvent,
        private val store: HSMStore,
        private val cryptoOpsClient: CryptoOpsClient
    ) : DownstreamAlwaysUpAbstractImpl() {
        companion object {
            private fun Map<String, String>.isPreferredPrivateKeyPolicy(policy: String): Boolean =
                this[CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_KEY] == policy
        }

        private val config = event.config.toCryptoConfig()

        private val hsmConfig = config.hsmService()

        private val workerSets = WorkerSets(config, store)

        private val executor = CryptoRetryingExecutor(
            logger,
            BackoffStrategy.createBackoff(hsmConfig.downstreamMaxAttempts, listOf(100L))
        )

        fun assignHSM(tenantId: String, category: String, context: Map<String, String>): HSMAssociationInfo {
            logger.info("assignHSM(tenant={}, category={})", tenantId, category)
            if(workerSets.isOnlySoftHSM) {
                logger.warn("There is only SOFT HSM configured, will assign that.")
                return assignSoftHSM(tenantId, category)
            }
            val existing = store.findTenantAssociation(tenantId, category)
            if(existing != null) {
                logger.warn(
                    "The ${existing.workerSetId} HSM already assigned for tenant={}, category={}",
                    tenantId,
                    category)
                ensureWrappingKey(existing)
                return HSMAssociationInfo(existing.workerSetId, existing.deprecatedAt)
            }
            val stats = workerSets.getHSMStats(category).filter { s -> s.allUsages < s.capacity }
            val chosen = if (context.isPreferredPrivateKeyPolicy(CryptoConsts.HSMContext.PREFERRED_PRIVATE_KEY_POLICY_ALIASED)) {
                tryChooseAliased(stats)
            } else {
                tryChooseAny(stats)
            }
            val association = store.associate(
                tenantId = tenantId,
                category = category,
                workerSetId = chosen.workerSetId,
                masterKeyPolicy = workerSets.getMasterKeyPolicy(chosen.workerSetId)
            )
            ensureWrappingKey(association)
            return HSMAssociationInfo(association.workerSetId, association.deprecatedAt)
        }

        fun assignSoftHSM(tenantId: String, category: String): HSMAssociationInfo {
            logger.info("assignSoftHSM(tenant={}, category={})", tenantId, category)
            val existing = store.findTenantAssociation(tenantId, category)
            if(existing != null) {
                logger.warn(
                    "The ${existing.workerSetId} HSM already assigned for tenant={}, category={}",
                    tenantId,
                    category)
                ensureWrappingKey(existing)
                return HSMAssociationInfo(existing.workerSetId, existing.deprecatedAt)
            }
            val association = store.associate(
                tenantId = tenantId,
                category = category,
                workerSetId = SOFT_HSM_WORKER_SET_ID,
                masterKeyPolicy = workerSets.getMasterKeyPolicy(SOFT_HSM_WORKER_SET_ID)
            )
            ensureWrappingKey(association)
            return HSMAssociationInfo(association.workerSetId, association.deprecatedAt)
        }

        fun findAssignedHSM(tenantId: String, category: String): HSMTenantAssociation? {
            logger.debug { "findAssignedHSM(tenant=$tenantId, category=$category)"  }
            return store.findTenantAssociation(tenantId, category)
        }

        private fun ensureWrappingKey(association: HSMTenantAssociation) {
            if (workerSets.getMasterKeyPolicy(association.workerSetId) == MasterKeyPolicy.UNIQUE) {
                require(!association.masterKeyAlias.isNullOrBlank()) {
                    "The master key alias is not specified."
                }
                executor.executeWithRetry {
                    cryptoOpsClient.createWrappingKey(
                        workerSetId = association.workerSetId,
                        failIfExists = false,
                        masterKeyAlias = association.masterKeyAlias!!,
                        context = mapOf(
                            CRYPTO_TENANT_ID to association.tenantId
                        )
                    )
                }
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