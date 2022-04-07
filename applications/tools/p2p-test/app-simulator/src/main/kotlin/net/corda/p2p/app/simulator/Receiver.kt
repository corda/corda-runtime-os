package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import java.time.Duration
import java.time.Instant

@Suppress("LongParameterList")
class Receiver(private val subscriptionFactory: SubscriptionFactory,
               private val receiveTopic: String,
               private val metadataTopic: String,
               private val kafkaServers: String,
               private val clients: Int,
               private val instanceId: String,
    ): Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val subscriptions = mutableListOf<Subscription<*, *>>()

    fun start() {
        (1..clients).forEach { client ->
            val subscriptionConfig = SubscriptionConfig("app-simulator-receiver", receiveTopic, )
            val kafkaConfig = SmartConfigImpl.empty()
                .withValue(BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(kafkaServers))
                .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef("$instanceId-$client".hashCode()))
            val subscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig,
                InboundMessageProcessor(metadataTopic), kafkaConfig, null)
            subscription.start()
            subscriptions.add(subscription)
        }
        logger.info("Started consuming messages fom $receiveTopic. When you want to stop the consumption, you can do so using Ctrl+C.")
    }

    override fun close() {
        subscriptions.forEach { it.stop() }
    }

    private inner class InboundMessageProcessor(val destinationTopic: String): EventLogProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
            val now = Instant.now()
            return events.map {
                val authenticatedMessage = it.value!!.message as AuthenticatedMessage
                val payload = objectMapper.readValue<MessagePayload>(authenticatedMessage.payload.array())
                val messageReceivedEvent = MessageReceivedEvent(payload.sender,
                    authenticatedMessage.header.messageId, payload.sendTimestamp, now, Duration.between(payload.sendTimestamp, now))
                Record(destinationTopic, messageReceivedEvent.messageId, objectMapper.writeValueAsString(messageReceivedEvent))
            }
        }
    }
}
