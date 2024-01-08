package net.corda.flow.messaging.mediator.fakes

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactoryFactory
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_POLL_TIMEOUT
import java.time.Duration

/**
 * Factory for creating multi-source event mediator consumers.
 */
class TestMediatorConsumerFactoryFactory(
    private val messageBus: TestMessageBus,
): MediatorConsumerFactoryFactory {
    companion object {
        const val POLL_RECORDS = "test.consumer.pollRecords"
        const val POLL_TIME_MS = "test.consumer.pollTimeMs"
        const val COMMIT_TIME_MS = "test.consumer.commitTimeMs"
        const val RESET_OFFSET_TIME_MS = "test.consumer.resetOffsetsTimeMs"
    }

    override fun createMessageBusConsumerFactory(
        topicName: String,
        groupName: String,
        messageBusConfig: SmartConfig,
    ) = TestMediatorConsumerFactory(
        topicName,
        messageBusConfig,
        messageBus,
    )

    class TestMediatorConsumerFactory(
        private val topicName: String,
        private val messageBusConfig: SmartConfig,
        private val messageBus: TestMessageBus,
    ): MediatorConsumerFactory {
        override fun <K : Any, V : Any> create(config: MediatorConsumerConfig<K, V>): MediatorConsumer<K, V> =
            TestMessageBusConsumer(
                topicName,
                messageBus,
                messageBusConfig.getInt(POLL_RECORDS),
                messageBusConfig.getLong(POLL_TIME_MS),
                messageBusConfig.getLong(MEDIATOR_PROCESSING_POLL_TIMEOUT),
                messageBusConfig.getLong(COMMIT_TIME_MS),
                messageBusConfig.getLong(RESET_OFFSET_TIME_MS),
            )
    }

    private class TestMessageBusConsumer<K: Any, V: Any>(
        private val topic: String,
        private val messageBus: TestMessageBus,
        private val pollRecords: Int,
        private val pollTimeMs: Long,
        private val pollTimeoutMs: Long,
        private val commitTimeMs: Long,
        private val resetOffsetsTimeMs: Long,
    ): MediatorConsumer<K, V> {
        override fun subscribe() {
            // Nothing to do here
        }

        override fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
            val records : List<CordaConsumerRecord<K, V>> = messageBus.poll(topic, pollRecords)
            if (records.isNotEmpty()) {
                Thread.sleep(pollTimeMs)
            } else {
                Thread.sleep(pollTimeoutMs)
            }
            return records
        }

        override fun syncCommitOffsets() {
            Thread.sleep(commitTimeMs)
        }

        override fun resetEventOffsetPosition() =
            Thread.sleep(resetOffsetsTimeMs)

        override fun close() {
            // Nothing to do here
        }
    }
}