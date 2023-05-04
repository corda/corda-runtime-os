package net.corda.membership.impl.persistence.client

import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Resource
import net.corda.membership.lib.MessagesHeaders.SENDER_ID
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future

internal class RequestSender(
    publisherFactory: PublisherFactory,
    subscriptionFactory: SubscriptionFactory,
    messagingConfig: SmartConfig,
    clientId: String,
    groupId: String,
) : Resource {
    private val id = UUID.randomUUID().toString()

    private val outstandingRequests = ConcurrentHashMap<String, CompletableFuture<MembershipPersistenceResponse>>()
    private val publisher = publisherFactory.createPublisher(
        PublisherConfig(
            "$clientId-$id",
        ),
        messagingConfig,
    ).also {
        it.start()
    }

    private val receiver = subscriptionFactory.createPubSubSubscription(
        subscriptionConfig = SubscriptionConfig(
            groupName = "$groupId-$id",
            eventTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_RESPONSE_TOPIC,
        ),
        messagingConfig = messagingConfig,
        processor = Processor(),
    ).also {
        it.start()
    }

    override fun close() {
        publisher.close()
        receiver.close()
    }

    fun send(request: MembershipPersistenceRequest): CompletableFuture<MembershipPersistenceResponse> {
        val future = CompletableFuture<MembershipPersistenceResponse>()
        outstandingRequests[request.context.requestId] = future
        publisher.publish(
            listOf(
                Record(
                    topic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                    key = request.context.requestId,
                    value = request,
                    headers = listOf(
                        SENDER_ID to id,
                    ),
                ),
            ),
        ).forEach {
            it.join()
        }
        return future
    }

    private inner class Processor : PubSubProcessor<String, MembershipPersistenceResponse> {
        override fun onNext(event: Record<String, MembershipPersistenceResponse>): Future<Unit> {
            val complete = CompletableFuture.completedFuture(Unit)
            if (!event.headers.any { it.first == SENDER_ID && it.second == id }) {
                return complete
            }
            val value = event.value ?: return complete
            outstandingRequests.remove(value.context.requestId)?.complete(value)
            return complete
        }

        override val keyClass = String::class.java
        override val valueClass = MembershipPersistenceResponse::class.java
    }
}
