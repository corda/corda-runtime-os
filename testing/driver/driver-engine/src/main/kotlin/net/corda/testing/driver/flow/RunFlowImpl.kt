package net.corda.testing.driver.flow

import java.time.Instant
import java.util.Collections.singletonList
import java.util.Deque
import java.util.LinkedList
import java.util.UUID
import java.util.function.Predicate
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.p2p.app.AppMessage
import net.corda.data.virtualnode.VirtualNodeInfo
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.p2p.filter.FlowP2PFilterProcessor
import net.corda.flow.pipeline.factory.FlowEventProcessorFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.utils.keyValuePairListOf
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.Schemas.Persistence.PERSISTENCE_ENTITY_PROCESSOR_TOPIC
import net.corda.schema.Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC
import net.corda.schema.Schemas.UniquenessChecker.UNIQUENESS_CHECK_TOPIC
import net.corda.schema.Schemas.Verification.VERIFICATION_LEDGER_PROCESSOR_TOPIC
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.testing.driver.config.SmartConfigProvider
import net.corda.testing.driver.node.FlowErrorException
import net.corda.testing.driver.node.FlowFatalException
import net.corda.testing.driver.node.RunFlow
import net.corda.testing.driver.processor.ExternalProcessor
import net.corda.testing.driver.processor.crypto.CryptoProcessor
import net.corda.testing.driver.processor.entity.EntityProcessor
import net.corda.testing.driver.processor.ledger.LedgerProcessor
import net.corda.testing.driver.processor.uniqueness.UniquenessProcessor
import net.corda.testing.driver.processor.verify.VerifyProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

typealias FlowEventRecord = Record<String, FlowEvent>

@Suppress("unused", "LongParameterList")
@Component(
    service = [ RunFlow::class ],
    reference = [
        Reference(name = FLOW_OPS_MESSAGE_TOPIC, service = CryptoProcessor::class),
        Reference(name = PERSISTENCE_ENTITY_PROCESSOR_TOPIC, service = EntityProcessor::class),
        Reference(name = PERSISTENCE_LEDGER_PROCESSOR_TOPIC, service = LedgerProcessor::class),
        Reference(name = UNIQUENESS_CHECK_TOPIC, service = UniquenessProcessor::class),
        Reference(name = VERIFICATION_LEDGER_PROCESSOR_TOPIC, service = VerifyProcessor::class)
    ]
)
class RunFlowImpl @Activate constructor(
    @Reference
    private val flowRecordFactory: FlowRecordFactory,
    @Reference
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference
    flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    @Reference
    smartConfigProvider: SmartConfigProvider,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val componentContext: ComponentContext
): RunFlow {
    companion object {
        private val flowContextProperties = keyValuePairListOf(mapOf("corda.account" to "account-zero"))

        private fun <T> MutableCollection<T>.extractAll(predicate: Predicate<T>): Deque<T> {
            val results = LinkedList<T>()
            val iter = iterator()
            while (iter.hasNext()) {
                val next = iter.next()
                if (predicate.test(next)) {
                    results.addLast(next)
                    iter.remove()
                }
            }
            return results
        }

        private fun Collection<Record<*, *>>.filterFlowEventsTo(events: Deque<FlowEventRecord>): Deque<FlowEventRecord> {
            for (record in this) {
                if (record.value is FlowEvent && record.key is String) {
                    @Suppress("unchecked_cast")
                    events.addLast(record as FlowEventRecord)
                }
            }
            return events
        }
    }

    private val smartConfig = smartConfigProvider.smartConfig
    private val flowMapperMessageProcessor = FlowMapperMessageProcessor(flowMapperEventExecutorFactory, smartConfig)
    private val flowP2PFilterProcessor = FlowP2PFilterProcessor(cordaAvroSerializationFactory)
    private val clientId = generateRandomId()

    private val externalProcessors = mutableMapOf<String, ExternalProcessor?>()

    private fun generateRandomId(): String = UUID.randomUUID().toString()

    private fun createRPCStartFlow(
        virtualNodeInfo: VirtualNodeInfo,
        flowClassName: String,
        flowStartArgs: String
    ): StartFlow {
        return StartFlow(
            FlowStartContext(
                FlowKey(clientId, virtualNodeInfo.holdingIdentity),
                FlowInitiatorType.RPC,
                clientId,
                virtualNodeInfo.holdingIdentity,
                virtualNodeInfo.cpiIdentifier.name,
                virtualNodeInfo.holdingIdentity,
                flowClassName,
                flowStartArgs,
                flowContextProperties,
                Instant.now(),
            ), flowStartArgs
        )
    }

    private fun startFlow(rpcStartFlow: StartFlow): StateAndEventProcessor.Response<FlowMapperState> {
        val initialFlowMapping = flowRecordFactory.createFlowMapperEventRecord("", rpcStartFlow)
        @Suppress("unchecked_cast")
        return flowMapperMessageProcessor.onNext(null, initialFlowMapping as Record<String, FlowMapperEvent>)
    }

    private fun processExternalEvent(responseEvent: Record<*, *>): List<Record<*, *>> {
        return externalProcessors.computeIfAbsent(responseEvent.topic) { topic ->
            componentContext.locateService<ExternalProcessor>(topic)
        }?.processEvent(responseEvent) ?: throw FlowFatalException("Received unknown topic: ${responseEvent.topic}")
    }

    @Suppress("NestedBlockDepth")
    override fun runFlow(virtualNodeInfo: VirtualNodeInfo, flowClassName: String, flowStartArgs: String): String? {
        return try {
            val initialResponse = startFlow(createRPCStartFlow(virtualNodeInfo, flowClassName, flowStartArgs))
            flowEventProcessorFactory.create(smartConfig).let { processor ->
                val checkpoints = mutableMapOf<String, Checkpoint>()
                val flowMapperStates = mutableMapOf<String, FlowMapperState>()
                val flowEvents = initialResponse.responseEvents.filterFlowEventsTo(LinkedList())

                while (flowEvents.isNotEmpty()) {
                    val current = flowEvents.removeFirst()

                    val responseEvents = processor.onNext(checkpoints[current.key], current).let { result ->
                        result.updatedState?.also { state ->
                            checkpoints[current.key] = state
                        }
                        LinkedList(result.responseEvents)
                    }

                    // Check whether the flow is either COMPLETED, FAILED or KILLED.
                    responseEvents.extractAll { evt ->
                        evt.topic == FLOW_STATUS_TOPIC
                    }.singleOrNull()?.also { statusEvent ->
                        val flowStatus = requireNotNull(statusEvent.value as? FlowStatus) {
                            "FlowStatus missing for $statusEvent"
                        }
                        if (flowStatus.initiatorType == FlowInitiatorType.RPC) {
                            when (flowStatus.flowStatus) {
                                FlowStates.COMPLETED ->
                                    return@let flowStatus.result

                                FlowStates.FAILED ->
                                    throw FlowErrorException(flowStatus.error.errorMessage)

                                FlowStates.KILLED ->
                                    throw FlowFatalException(flowStatus.processingTerminatedReason)

                                else -> {}
                            }
                        }
                    }

                    @Suppress("unchecked_cast")
                    while (responseEvents.isNotEmpty()) {
                        val responseEvent = responseEvents.removeFirst()
                        when (responseEvent.topic) {
                            FLOW_EVENT_TOPIC ->
                                flowEvents += responseEvent as FlowEventRecord

                            FLOW_MAPPER_EVENT_TOPIC -> {
                                val key = responseEvent.key as String
                                val response = flowMapperMessageProcessor.onNext(
                                    flowMapperStates[key], responseEvent as Record<String, FlowMapperEvent>
                                )
                                response.updatedState?.also { state ->
                                    flowMapperStates[key] = state
                                }
                                responseEvents += response.responseEvents
                            }

                            P2P_OUT_TOPIC -> {
                                val events = singletonList(responseEvent as Record<String, AppMessage>)
                                responseEvents += flowP2PFilterProcessor.onNext(events)
                            }

                            else ->
                                processExternalEvent(responseEvent).filterFlowEventsTo(flowEvents)
                        }
                    }
                }

                // No more FlowEvents, but we have nothing to return!?
                null
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }
}
