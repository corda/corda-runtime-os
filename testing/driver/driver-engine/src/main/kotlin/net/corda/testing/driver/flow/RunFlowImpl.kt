package net.corda.testing.driver.flow

import java.time.Duration
import java.time.Instant
import java.util.Collections.singletonList
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
import net.corda.flow.pipeline.exceptions.FlowEventException
import net.corda.flow.pipeline.exceptions.FlowFatalException
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
import net.corda.schema.Schemas.Persistence.PERSISTENCE_LEDGER_PROCESSOR_TOPIC
import net.corda.session.mapper.service.executor.FlowMapperMessageProcessor
import net.corda.testing.driver.node.RunFlow
import net.corda.testing.driver.config.SmartConfigProvider
import net.corda.testing.driver.crypto.CryptoProcessor
import net.corda.testing.driver.flow.RunFlowImpl.Companion.CRYPTO_PROCESSOR
import net.corda.testing.driver.flow.RunFlowImpl.Companion.LEDGER_PROCESSOR
import net.corda.testing.driver.ledger.LedgerProcessor
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.ComponentContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused", "LongParameterList")
@Component(
    service = [ RunFlow::class ],
    reference = [
        Reference(name = CRYPTO_PROCESSOR, service = CryptoProcessor::class),
        Reference(name = LEDGER_PROCESSOR, service = LedgerProcessor::class)
    ]
)
class RunFlowImpl @Activate constructor(
    @Reference
    private val flowRecordFactory: FlowRecordFactory,
    @Reference
    private val flowEventProcessorFactory: FlowEventProcessorFactory,
    @Reference
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    @Reference
    smartConfigProvider: SmartConfigProvider,
    @Reference
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    componentContext: ComponentContext
): RunFlow {
    companion object {
        const val CRYPTO_PROCESSOR = "CryptoProcessor"
        const val LEDGER_PROCESSOR = "LedgerProcessor"

        private val flowContextProperties = keyValuePairListOf(mapOf("corda.account" to "account-zero"))
        private val logger = LoggerFactory.getLogger(RunFlowImpl::class.java)
        private val TIMEOUT = Duration.ofSeconds(60)

        private fun <T> MutableCollection<T>.extractAll(predicate: Predicate<T>): MutableList<T> {
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
    }

    private val smartConfig = smartConfigProvider.smartConfig
    private val flowMapperMessageProcessor = FlowMapperMessageProcessor(flowMapperEventExecutorFactory, smartConfig)
    private val flowP2PFilterProcessor = FlowP2PFilterProcessor(cordaAvroSerializationFactory)
    private val clientId = generateRandomId()

    private val cryptoProcessor by lazy {
        componentContext.locateService<CryptoProcessor>(CRYPTO_PROCESSOR)
    }

    private val ledgerProcessor by lazy {
        componentContext.locateService<LedgerProcessor>(LEDGER_PROCESSOR)
    }

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

    @Suppress("ComplexMethod", "NestedBlockDepth", "unchecked_cast")
    override fun runFlow(virtualNodeInfo: VirtualNodeInfo, flowClassName: String, flowStartArgs: String): String? {
        return try {
            val initialResponse = startFlow(createRPCStartFlow(virtualNodeInfo, flowClassName, flowStartArgs))
            flowEventProcessorFactory.create(smartConfig).let { processor ->
                var flowResult: String? = null
                val checkpoints = mutableMapOf<String, Checkpoint>()
                val flowMapperStates = mutableMapOf<String, FlowMapperState>()
                val pending = LinkedList(initialResponse.responseEvents as List<Record<String, FlowEvent>>)

                while (pending.isNotEmpty()) {
                    val current = pending.removeFirst()

                    val responseEvents = processor.onNext(checkpoints[current.key], current).let { result ->
                        result.updatedState?.also { state ->
                            checkpoints[current.key] = state
                        }
                        LinkedList(result.responseEvents)
                    }

                    // Check whether the flow is either COMPLETED, FAILED or KILLED.
                    val statusEvent = responseEvents.extractAll { evt ->
                        evt.topic == FLOW_STATUS_TOPIC
                    }.singleOrNull()

                    if (statusEvent != null) {
                        val flowStatus = requireNotNull((statusEvent as? Record<String, FlowStatus>)?.value) {
                            "FlowStatus missing for $statusEvent"
                        }
                        if (flowStatus.initiatorType == FlowInitiatorType.RPC) {
                            when (flowStatus.flowStatus) {
                                FlowStates.COMPLETED -> {
                                    flowResult = flowStatus.result
                                    break
                                }

                                FlowStates.FAILED ->
                                    throw FlowEventException(flowStatus.error.errorMessage)

                                FlowStates.KILLED ->
                                    throw FlowFatalException(flowStatus.processingTerminatedReason)

                                else -> {}
                            }
                        }
                    }

                    while (responseEvents.isNotEmpty()) {
                        val responseEvent = responseEvents.removeFirst()
                        when (responseEvent.topic) {
                            FLOW_EVENT_TOPIC ->
                                pending += responseEvent as Record<String, FlowEvent>

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

                            FLOW_OPS_MESSAGE_TOPIC ->
                                pending += cryptoProcessor.processEvent(responseEvent)

                            PERSISTENCE_LEDGER_PROCESSOR_TOPIC ->
                                pending += ledgerProcessor.processEvent(responseEvent)

                            P2P_OUT_TOPIC -> {
                                val events = singletonList(responseEvent as Record<String, AppMessage>)
                                responseEvents += flowP2PFilterProcessor.onNext(events)
                            }

                            else -> {
                                logger.warn("Received unknown topic: {}", responseEvent.topic)
                            }
                        }
                    }
                }
                flowResult
            }
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw CordaRuntimeException(e::class.java.name, e.message, e)
        }
    }
}
