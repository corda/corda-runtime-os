package net.corda.ledger.persistence.query.execution.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.flow.application.persistence.query.ResultSetImpl
import net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory
import net.corda.ledger.persistence.query.execution.VaultNamedQueryExecutor
import net.corda.ledger.persistence.query.registration.VaultNamedQueryRegistry
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.utils.transaction
import net.corda.persistence.common.EntitySandboxService
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.serialization.SerializationService
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

@Component(service = [VaultNamedQueryExecutor::class])
class VaultNamedQueryExecutorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = ExternalEventResponseFactory::class)
    private val externalEventResponseFactory: ExternalEventResponseFactory,
    @Reference(service = EntitySandboxService::class)
    private val entitySandboxService: EntitySandboxService,
    @Reference(service = VaultNamedQueryRegistry::class)
    private val registry: VaultNamedQueryRegistry,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
): VaultNamedQueryExecutor {

    private companion object {
        // TODO Should we hardcode this?
        private const val UTXO_RELEVANT_TX_TABLE = "utxo_relevant_transaction_state"

        const val CONFIG_HANDLE = "CONFIG_HANDLE"
        const val SUBSCRIPTION = "SUBSCRIPTION"
        const val GROUP_NAME = "persistence.ledger"

        private val log = LoggerFactory.getLogger(VaultNamedQueryExecutorImpl::class.java)
    }

    private val lifecycleCoordinator: LifecycleCoordinator =
        coordinatorFactory.createCoordinator<VaultNamedQueryExecutor>(::eventHandler)

    private val dependentComponents = DependentComponents.of()

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        log.info("Vault Named Query Executor starting")
        lifecycleCoordinator.start()
    }

    override fun stop() {
        log.info("Vault Named Query Executor stopping")
        lifecycleCoordinator.stop()
    }

    override fun executeQuery(
        request: ExecuteVaultNamedQueryRequest
    ): PagedQuery.ResultSet<ByteBuffer> {
        val vaultNamedQuery = registry.getQuery(request.queryName)

        require(vaultNamedQuery != null) { "Query with name ${request.queryName} could not be found!" }

        val (resultList, newOffset) = entitySandboxService.get(request.holdingIdentity.toCorda())
            .getEntityManagerFactory()
            .transaction { em ->
                val query = em.createQuery(
                    "SELECT * FROM $UTXO_RELEVANT_TX_TABLE " +
                            vaultNamedQuery.whereJson
                )

                request.queryParameters.forEach {
                    query.setParameter(it.key, serializationService.deserialize(it.value.array(), Any::class.java))
                }

                val originalResultList = query.resultList

                query.firstResult = request.offset
                query.maxResults = request.limit

                Pair(query.resultList, originalResultList.size - request.limit)
            }

        return ResultSetImpl(
            if (newOffset > 0) newOffset else 0, // We don't want any negative numbers so just reset to 0
            resultList.size,
            newOffset > 0,
            resultList.filterNotNull().map { ByteBuffer.wrap(serializationService.serialize(it).bytes) }
        )
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.info("Vault Named Query Executor received event $event")
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
                dependentComponents.registerAndStartAll(coordinator)
            }
            is StopEvent -> {
                dependentComponents.stopAll()
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Vault Named Query Executor is ${event.status}")

                if (event.status == LifecycleStatus.UP) {
                    coordinator.createManagedResource(CONFIG_HANDLE) {
                        configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.MESSAGING_CONFIG)
                        )
                    }
                } else {
                    coordinator.closeManagedResources(setOf(CONFIG_HANDLE))
                }

                coordinator.updateStatus(event.status)
            }
            is ConfigChangedEvent -> {
                log.info("Received configuration change event, (re)initialising subscription")
                initialiseSubscription(event.config.getConfig(ConfigKeys.MESSAGING_CONFIG))
            }
            else -> {
                log.warn("Unexpected event ${event}, ignoring")
            }
        }
    }

    private fun initialiseSubscription(config: SmartConfig) {
        lifecycleCoordinator.createManagedResource(SUBSCRIPTION) {
            subscriptionFactory.createDurableSubscription(
                SubscriptionConfig(GROUP_NAME, Schemas.Persistence.PERSISTENCE_LEDGER_NAMED_QUERY_TOPIC),
                VaultNamedQueryProcessor(
                    this,
                    externalEventResponseFactory
                ),
                config,
                null
            ).also {
                it.start()
            }
        }
    }
}