package net.corda.flow.maintenance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.MDC_CLIENT_ID
import net.corda.utilities.debug
import net.corda.utilities.trace
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component(service = [RecoverNotarizedTransactionsScheduledTaskProcessor::class])
class RecoverNotarizedTransactionsScheduledTaskProcessorImpl @Activate constructor(
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : RecoverNotarizedTransactionsScheduledTaskProcessor {
    companion object {
        private val logger = LoggerFactory.getLogger(RecoverNotarizedTransactionsScheduledTaskProcessor::class.java)
    }

    private val coordinator = coordinatorFactory.createCoordinator<RecoverNotarizedTransactionsScheduledTaskProcessor>(::eventHandler)
    private var stateManager: StateManager? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        // Top level component is using ConfigurationReadService#registerComponentForUpdates, so all the below keys
        // should be present.
        val requiredKeys = listOf(ConfigKeys.UTXO_LEDGER_CONFIG)
        if (requiredKeys.all { config.containsKey(it) }) {
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
//            val ledgerConfig = config.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG)

            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()

            /**
             * Task and executor for the cleanup of checkpoints that are idle or timed out.
             */
            coordinator.createManagedResource("RECOVER_NOTARIZED_TRANSACTIONS") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "recover.notarized.transactions.task",
                        Schemas.ScheduledTask.SCHEDULE_TASK_TOPIC_MISSED_NOTARIZED_TRANSACTION_RECOVERY_PROCESSOR
                    ),
                    ActualProcessor(cpiInfoReadService, virtualNodeInfoReadService),
//                    messagingConfig(virtualNodeInfoReadService),
                    messagingConfig,
                    null
                )
            }.start()
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug { "Recovery flow event $event." }

        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                logger.trace { "Recovery flow task processor is stopping..." }
                subscriptionRegistrationHandle?.close()
                stateManager?.stop()
            }
        }
    }

    private class ActualProcessor(
        private val cpiInfoReadService: CpiInfoReadService,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        private val messageFactory: StartFlowMessageFactoryImpl = StartFlowMessageFactoryImpl()
    ) : DurableProcessor<String, ScheduledTaskTrigger> {
        private companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        override val keyClass = String::class.java
        override val valueClass = ScheduledTaskTrigger::class.java

        private val objectMapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        }

        override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
            logger.info("Processing ${events.size} scheduled event for notarized transaction recovery")

            val records = mutableListOf<Record<*, *>>()

            virtualNodeInfoReadService
                .getAll()
                .filter(::hasUtxoWithNotaryCpk)
                .map(::createRecoveryFlowStartEvent)
                .forEach { (shortHash, start, status) ->
                    records += Record(
                        Schemas.Flow.FLOW_MAPPER_START,
                        getKeyForStartEvent(status.key, shortHash.value),
                        start
                    )
                    records += Record(Schemas.Flow.FLOW_STATUS_TOPIC, status.key, status)
                }

            logger.info("Created recovery start events = $records")

            return records
        }

        private fun hasUtxoWithNotaryCpk(virtualNode: VirtualNodeInfo): Boolean {
            return cpiInfoReadService.get(virtualNode.cpiIdentifier)?.let { cpiMetadata ->
                val hasContracts = cpiMetadata.cpksMetadata.any { it.isContractCpk() }
                val isNotary = cpiMetadata.cpiId.name.contains("notary")
                logger.info("Filtering - ${virtualNode.holdingIdentity.shortHash} - hasContracts = $hasContracts | isNotary = $isNotary")
//                val hasNotaryClientFlow = cpiMetadata.cpksMetadata.any { it.cordappManifest.notaryPluginFlows.isNotEmpty() }
                hasContracts && !isNotary // && hasNotaryClientFlow
            } ?: false
        }

        private fun createRecoveryFlowStartEvent(virtualNode: VirtualNodeInfo): Triple<ShortHash, FlowMapperEvent, FlowStatus> {
            val virtualNodeAvro = virtualNode.toAvro()
            val instant = Instant.now().minus(5, ChronoUnit.MINUTES)
            val clientRequestId =
                "recover-notarized-transactions-${virtualNode.holdingIdentity.shortHash}-${instant.toEpochMilli()}"

            // TODO Platform properties to be populated correctly.
            // This is a placeholder which indicates access to everything, see CORE-6076
            val flowContextPlatformProperties = mapOf(
                "corda.account" to "account-zero",
                MDC_CLIENT_ID to clientRequestId
            )

            val start = messageFactory.createStartFlowEvent(
                clientRequestId,
                virtualNodeAvro,
                flowClassName = "com.r3.corda.notary.plugin.common.recovery.NotarizedTransactionRecoveryFlow",
                flowStartArgs = objectMapper.writeValueAsString(mapOf("instant" to instant)),
                flowContextPlatformProperties
            )

            val status = messageFactory.createStartFlowStatus(
                clientRequestId,
                virtualNodeAvro,
                flowClassName = "com.r3.corda.notary.plugin.common.recovery.NotarizedTransactionRecoveryFlow"
            )

            return Triple(virtualNode.holdingIdentity.shortHash, start, status)
        }

        private fun getKeyForStartEvent(flowKey: FlowKey, holdingIdentityShortHash: String): String {
            return "${flowKey.id}-${holdingIdentityShortHash}"
        }
    }
}
