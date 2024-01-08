package net.corda.flow.messaging.mediator.fakes

import net.corda.data.KeyValuePairList
import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.identity.HoldingIdentity
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.schema.Schemas.Flow.FLOW_START
import java.time.Instant
import java.util.UUID

class TestLoadGenerator(
    private val cpiName: String,
    private val holdingIdentity: HoldingIdentity,
    private val flowClassName: String,
    private val flowStartArgs: String,
) : TestMessageBus {

    private var count = 0
    @Suppress("UNCHECKED_CAST")
    override fun <K, V> poll(topic: String, pollRecords: Int): List<CordaConsumerRecord<K, V>> {
        return when (topic) {
             FLOW_START ->
                 if (count == 0) {
                     count++
                (1..pollRecords).map {
                    val flowId = UUID.randomUUID().toString()
                    val flowEvent = createStartFlowEvent(
                        "clientId",
                        cpiName,
                        holdingIdentity,
                        flowClassName,
                        flowStartArgs,
                    )
                    CordaConsumerRecord(
                        topic = "",
                        partition = -1,
                        offset = -1,
                        key = flowId as K,
                        value = flowEvent as V,
                        timestamp = 0
                    )
                }
            } else emptyList()

            else -> emptyList()
        }
    }

    override fun send(topic: String, message: MediatorMessage<*>) {
        // Do nothing
    }
    private fun createStartFlowEvent(
        clientRequestId: String,
        cpiName: String,
        holdingIdentity: HoldingIdentity,
        flowClassName: String,
        flowStartArgs: String,
    ): FlowEvent {
        val context = FlowStartContext(
            FlowKey(clientRequestId, holdingIdentity),
            FlowInitiatorType.RPC,
            clientRequestId,
            holdingIdentity,
            cpiName,
            holdingIdentity,
            flowClassName,
            flowStartArgs,
            KeyValuePairList(emptyList()),
            Instant.now()
        )

        val flowId = UUID.randomUUID().toString()
        val startFlowEvent = StartFlow(context, flowStartArgs)
        return FlowEvent(flowId, startFlowEvent)
    }
}