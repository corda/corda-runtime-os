package net.corda.flow.maintenance

import com.fasterxml.jackson.databind.ObjectMapper
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
import net.corda.membership.lib.MemberInfoExtension.Companion.isNotary
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.LedgerConfig
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

@Component(service = [LedgerRepairScheduledTaskProcessor::class])
class LedgerRepairScheduledTaskProcessorImpl @Activate constructor(
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : LedgerRepairScheduledTaskProcessor {
    private companion object {
        private val logger = LoggerFactory.getLogger(LedgerRepairScheduledTaskProcessor::class.java)
    }

    private val coordinator = coordinatorFactory.createCoordinator<LedgerRepairScheduledTaskProcessor>(::eventHandler)
    private var stateManager: StateManager? = null
    private var subscriptionRegistrationHandle: RegistrationHandle? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        val requiredKeys = listOf(ConfigKeys.UTXO_LEDGER_CONFIG, ConfigKeys.MESSAGING_CONFIG)
        if (requiredKeys.all { config.containsKey(it) }) {
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            // close the lifecycle registration first to prevent down being signaled
            subscriptionRegistrationHandle?.close()
            
            coordinator.createManagedResource("LEDGER_REPAIR") {
                subscriptionFactory.createDurableSubscription(
                    SubscriptionConfig(
                        "ledger.repair.task",
                        Schemas.ScheduledTask.SCHEDULE_TASK_TOPIC_LEDGER_REPAIR_PROCESSOR
                    ),
                    Processor(
                        config.getConfig(ConfigKeys.UTXO_LEDGER_CONFIG),
                        cpiInfoReadService,
                        membershipGroupReaderProvider,
                        virtualNodeInfoReadService
                    ),
                    messagingConfig,
                    partitionAssignmentListener = null
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
        logger.debug { "Ledger repair scheduled task processor lifecycle event $event." }

        when (event) {
            is StartEvent -> {
                coordinator.updateStatus(LifecycleStatus.UP)
            }

            is StopEvent -> {
                logger.trace { "Ledger repair scheduled task processor is stopping..." }
                subscriptionRegistrationHandle?.close()
                stateManager?.stop()
            }
        }
    }

    private class Processor(
        ledgerConfig: SmartConfig,
        private val cpiInfoReadService: CpiInfoReadService,
        private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
        private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
        private val messageFactory: StartFlowMessageFactoryImpl = StartFlowMessageFactoryImpl()
    ) : DurableProcessor<String, ScheduledTaskTrigger> {
        private companion object {
            private const val REPAIR_FLOW = "com.r3.corda.notary.plugin.common.repair.NotarizedTransactionRepairFlow"
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        override val keyClass = String::class.java
        override val valueClass = ScheduledTaskTrigger::class.java

        private val objectMapper = ObjectMapper()

        private val runtimeDuration = ledgerConfig.getDuration(LedgerConfig.UTXO_LEDGER_REPAIR_RUNTIME_DURATION).toSeconds()
        private val fromDuration = ledgerConfig.getDuration(LedgerConfig.UTXO_LEDGER_REPAIR_FROM_DURATION)
        private val untilDuration = ledgerConfig.getDuration(LedgerConfig.UTXO_LEDGER_REPAIR_UNTIL_DURATION)

        override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
            logger.debug { "Processing ${events.size} scheduled event(s) to trigger ledger repair" }

            val records = mutableListOf<Record<*, *>>()

            virtualNodeInfoReadService
                .getAll()
                .filter(::hasUtxoAndNotANotary)
                .map(::createRepairFlowStartEvent)
                .forEach { (shortHash, start, status) ->
                    records += Record(
                        Schemas.Flow.FLOW_MAPPER_START,
                        getKeyForStartEvent(status.key, shortHash.value),
                        start
                    )
                    records += Record(Schemas.Flow.FLOW_STATUS_TOPIC, status.key, status)
                }

            return records
        }

        private fun hasUtxoAndNotANotary(virtualNode: VirtualNodeInfo): Boolean {
            val hasContracts = cpiInfoReadService.get(virtualNode.cpiIdentifier)?.let { cpiMetadata ->
                cpiMetadata.cpksMetadata.any { it.isContractCpk() }
            } ?: false

            if (!hasContracts) {
                return false
            }

            val isNotary = try {
                membershipGroupReaderProvider
                    .getGroupReader(virtualNode.holdingIdentity)
                    .lookup(virtualNode.holdingIdentity.x500Name)
                    ?.isNotary() ?: false
            } catch (e: IllegalStateException) {
                // If the member does not exist, then we should return false and not schedule anything
                return false
            }

            return !isNotary
        }

        private fun createRepairFlowStartEvent(virtualNode: VirtualNodeInfo): Triple<ShortHash, FlowMapperEvent, FlowStatus> {
            val virtualNodeAvro = virtualNode.toAvro()
            val until = Instant.now().minus(untilDuration)
            val from = until.minus(fromDuration)
            val clientRequestId =
                "ledger-repair-${virtualNode.holdingIdentity.shortHash}-${until.toEpochMilli()}"

            // TODO Platform properties to be populated correctly.
            // This is a placeholder which indicates access to everything, see CORE-6076
            val flowContextPlatformProperties = mapOf(
                "corda.account" to "account-zero",
                MDC_CLIENT_ID to clientRequestId
            )

            val start = messageFactory.createStartFlowEvent(
                clientRequestId,
                virtualNodeAvro,
                flowClassName = REPAIR_FLOW,
                flowStartArgs = objectMapper.writeValueAsString(
                    mapOf(
                        "from" to from.toEpochMilli(),
                        "until" to until.toEpochMilli(),
                        "duration" to runtimeDuration
                    )
                ),
                flowContextPlatformProperties
            )

            val status = messageFactory.createStartFlowStatus(
                clientRequestId,
                virtualNodeAvro,
                flowClassName = REPAIR_FLOW
            )

            return Triple(virtualNode.holdingIdentity.shortHash, start, status)
        }

        private fun getKeyForStartEvent(flowKey: FlowKey, holdingIdentityShortHash: String): String {
            return "${flowKey.id}-$holdingIdentityShortHash"
        }
    }
}
