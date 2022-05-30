package net.corda.processors.crypto.tests.infra

import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.test.util.eventually
import org.junit.jupiter.api.Assertions
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class FlowOpsResponses(
    messagingConfig: SmartConfig,
    subscriptionFactory: SubscriptionFactory
) : DurableProcessor<String, FlowOpsResponse>, AutoCloseable {

    private val subscription: Subscription<String, FlowOpsResponse> =
        subscriptionFactory.createDurableSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = "TEST",
                eventTopic = RESPONSE_TOPIC
            ),
            processor = this,
            messagingConfig = messagingConfig,
            partitionAssignmentListener = null
        ).also { it.start() }

    private val receivedEvents = ConcurrentHashMap<String, FlowOpsResponse?>()

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<FlowOpsResponse> = FlowOpsResponse::class.java

    override fun onNext(events: List<Record<String, FlowOpsResponse>>): List<Record<*, *>> {
        events.forEach {
            receivedEvents[it.key] = it.value
        }
        return emptyList()
    }

    fun waitForResponse(key: String): FlowOpsResponse =
        eventually(duration = Duration.ofSeconds(20)) {
            val event = receivedEvents[key]
            Assertions.assertNotNull(event)
            event!!
        }

    override fun close() {
        subscription.close()
    }
}