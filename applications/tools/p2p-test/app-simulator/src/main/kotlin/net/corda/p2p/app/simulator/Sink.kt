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
import net.corda.p2p.app.simulator.AppSimulator.Companion.KAFKA_BOOTSTRAP_SERVER_KEY
import net.corda.p2p.app.simulator.AppSimulator.Companion.PRODUCER_CLIENT_ID
import net.corda.v5.base.util.contextLogger
import java.io.Closeable
import java.sql.Connection
import java.sql.Timestamp

class Sink(private val subscriptionFactory: SubscriptionFactory,
           private val dbConnection: Connection,
           private val kafkaServers: String,
           private val clients: Int): Closeable {

    companion object {
        private val logger = contextLogger()
    }

    private val writeReceivedStmt = dbConnection.prepareStatement("INSERT INTO received_messages " +
            "(sender_id, message_id, sent_timestamp, received_timestamp, delivery_latency_ms) " +
            "VALUES (?, ?, ?, ?, ?) on conflict do nothing")
    private val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    private val subscriptions = mutableListOf<Subscription<*, *>>()

    fun start() {
        (1..clients).forEach { client ->
            val subscriptionConfig = SubscriptionConfig("app-simulator-sink", AppSimulator.DELIVERED_MSG_TOPIC, client)
            val kafkaConfig = SmartConfigImpl.empty()
                .withValue(KAFKA_BOOTSTRAP_SERVER_KEY, ConfigValueFactory.fromAnyRef(kafkaServers))
                .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("app-simulator-sink-$client"))
            val subscription = subscriptionFactory.createEventLogSubscription(subscriptionConfig, DBSinkProcessor(), kafkaConfig, null)
            subscription.start()
            subscriptions.add(subscription)
        }
        logger.info("Started forwarding messages to the DB. When you want to stop it, you can do so using Ctrl+C.")
    }

    override fun close() {
        subscriptions.forEach { it.stop() }
    }

    private inner class DBSinkProcessor: EventLogProcessor<String, String> {

        override val keyClass: Class<String>
            get() = String::class.java
        override val valueClass: Class<String>
            get() = String::class.java

        override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
            val messageReceivedEvents = events.map {
                objectMapper.readValue<MessageReceivedEvent>(it.value!!)
            }

            writeReceivedMessagesToDB(messageReceivedEvents)

            return emptyList()
        }

        private fun writeReceivedMessagesToDB(messages: List<MessageReceivedEvent>) {
            messages.forEach { messageReceivedEvent ->
                writeReceivedStmt.setString(1, messageReceivedEvent.sender)
                writeReceivedStmt.setString(2, messageReceivedEvent.messageId)
                writeReceivedStmt.setTimestamp(3, Timestamp.from(messageReceivedEvent.sendTimestamp))
                writeReceivedStmt.setTimestamp(4, Timestamp.from(messageReceivedEvent.receiveTimestamp))
                writeReceivedStmt.setLong(5, messageReceivedEvent.deliveryLatency.toMillis())
                writeReceivedStmt.addBatch()
            }
            writeReceivedStmt!!.executeBatch()
            dbConnection.commit()
        }

    }

}
