package net.corda.test.flow.external.events

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.external.ExternalEventResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.test.util.eventually
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

/**
 * Helper class for listening to responses to requests that use the external events API. This can be
 * useful for testing components that receive messages from the Corda message bus (and do not have
 * visibility of the senders external event service), but will respond using the
 * [ExternalEventResponseFactory][net.corda.flow.external.events.responses.factory.ExternalEventResponseFactory].
 */
class TestExternalEventResponseMonitor(
    private val subscriptionFactory: SubscriptionFactory,
    private val messagingConfig: SmartConfig
) {
    private companion object {
        const val SUBSCRIPTION_GROUP_NAME = "test-ext-event-response-monitor"
    }

    /**
     * Retrieves a map of external event request ids and their corresponding responses for a
     * specified list of request ids. This call will block until either responses for all the
     * specified request ids are available, or the specified [timeout] is reached.
     *
     * Will throw an exception if any of the request ids do not have a valid response returned
     * within the specified timeout period.
     */
    fun getResponses(
        requestIds: Collection<String>,
        timeout: Duration = Duration.ofSeconds(15)
    ) : Map<String, ExternalEventResponse> {

        val responses = requestIds.associateWith { CompletableFuture<ExternalEventResponse>() }

        val responseSubscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(SUBSCRIPTION_GROUP_NAME, Schemas.Flow.FLOW_EVENT_TOPIC),
            object : CompactedProcessor<String, FlowEvent> {
                override val keyClass = String::class.java
                override val valueClass = FlowEvent::class.java

                override fun onSnapshot(currentData: Map<String, FlowEvent>) =
                    currentData.values.forEach { processEvent(it) }

                override fun onNext(
                    newRecord: Record<String, FlowEvent>,
                    oldValue: FlowEvent?,
                    currentData: Map<String, FlowEvent>
                ) = processEvent(newRecord.value)

                private fun processEvent(event: FlowEvent?) {
                    (event?.payload as? ExternalEventResponse)?.let { eeResponse ->
                        responses[eeResponse.requestId]?.complete(eeResponse)
                    }
                }
            },
            messagingConfig = messagingConfig
        ).also { it.start() }

        try {
            eventually(duration = timeout) {
                assert(responses.all { it.value.isDone })
            }
        } catch (e: AssertionError) {
            throw TimeoutException("Not all responses were received before the timeout expired. " +
                    "Responses missing for the following request ids: " +
                    "${responses.filterValues { !it.isDone }.keys}")
        } finally {
            responseSubscription.close()
        }

        return responses.mapValues { it.value.get() }
    }
}
