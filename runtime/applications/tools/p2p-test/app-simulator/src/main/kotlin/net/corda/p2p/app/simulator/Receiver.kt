package net.corda.p2p.app.simulator

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.ConfigValueFactory
import net.corda.comp.kafka.topic.admin.KafkaTopicAdmin
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.app.simulator.AppSimulator.Companion.APP_SIMULATOR_SUBSYSTEM
import net.corda.p2p.app.simulator.AppSimulatorTopicCreator.Companion.APP_RECEIVED_MESSAGES_TOPIC
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.time.Instant

class Receiver(private val subscriptionFactory: SubscriptionFactory,
               private val configMerger: ConfigMerger,
               private val topicAdmin: KafkaTopicAdmin,
               private val commonConfig: CommonConfig,
               private val topicCreationParams: TopicCreationParams
    ): Closeable {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val subscriptions = mutableListOf<Subscription<*, *>>()

    fun start() {
        AppSimulatorTopicCreator(commonConfig.bootConfig, topicAdmin, topicCreationParams).createTopic()
        (1..commonConfig.clients).forEach { client ->
            val subscriptionConfig = SubscriptionConfig("app-simulator-receiver", commonConfig.parameters.receiveTopic,)
            val configWithInstanceId = commonConfig.bootConfig.withValue(
                INSTANCE_ID,
                ConfigValueFactory.fromAnyRef("${commonConfig.parameters.instanceId}-$client".hashCode())
            )
            val messagingConfig = configMerger.getMessagingConfig(configWithInstanceId)

            val subscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig,
                InboundMessageProcessor(APP_RECEIVED_MESSAGES_TOPIC), messagingConfig, null)
            subscription.start()
            subscriptions.add(subscription)
        }
        logger.info("Started consuming messages fom ${commonConfig.parameters.receiveTopic}. When you want to stop the consumption, you " +
                "can do so using Ctrl+C.")
    }

    override fun close() {
        subscriptions.forEach { it.close() }
    }

    private inner class InboundMessageProcessor(val destinationTopic: String): EventLogProcessor<String, AppMessage> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<AppMessage>
            get() = AppMessage::class.java

        override fun onNext(events: List<EventLogRecord<String, AppMessage>>): List<Record<*, *>> {
            val now = Instant.now()
            return events.mapNotNull {
                val authenticatedMessage = it.value!!.message as AuthenticatedMessage
                //Only JSON deserialize messages from another app-simulator (not sent by the MGM for example).
                if (authenticatedMessage.header.subsystem == APP_SIMULATOR_SUBSYSTEM) {
                    val payload = objectMapper.readValue<MessagePayload>(authenticatedMessage.payload.array())
                    val messageReceivedEvent = MessageReceivedEvent(payload.sender,
                        authenticatedMessage.header.messageId, payload.sendTimestamp, now, Duration.between(payload.sendTimestamp, now))
                    Record(destinationTopic, messageReceivedEvent.messageId, objectMapper.writeValueAsString(messageReceivedEvent))
                } else {
                    null
                }
            }
        }
    }
}
