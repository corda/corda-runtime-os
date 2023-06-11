package net.corda.messagebus.kafka.processor

import io.confluent.parallelconsumer.ParallelConsumerOptions
import io.confluent.parallelconsumer.ParallelStreamProcessor
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.processor.CordaProcessor
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.kafka.config.ResolvedConsumerConfig
import net.corda.messagebus.kafka.utils.toCordaConsumerRecord
import net.corda.messagebus.kafka.utils.toCordaTopicPartition
import net.corda.messagebus.kafka.utils.toKafkaRecord
import net.corda.messagebus.kafka.utils.toTopicPartitions
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory


class CordaKafkaParallelProcessor<K : Any, V : Any>(
    private val config: ResolvedConsumerConfig,
    val consumer: KafkaConsumer<Any, Any>,
    options: ParallelConsumerOptions<Any, Any>
) : CordaProcessor<K, V> {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val processor: ParallelStreamProcessor<Any, Any>

    init {
        processor = ParallelStreamProcessor.createEosStreamProcessor(options)
    }

    override fun subscribe(topic: String, rebalanceListener: CordaConsumerRebalanceListener?) {
        val listener = rebalanceListener?.let {
            object : ConsumerRebalanceListener {
                override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                    rebalanceListener.onPartitionsRevoked(partitions.map {
                        CordaTopicPartition(it.topic(), it.partition())
                    })
                }

                override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                    rebalanceListener.onPartitionsAssigned(partitions.map {
                        CordaTopicPartition(it.topic(), it.partition())
                    })
                }
            }
        }
        processor.subscribe(listOf(config.topicPrefix + topic), listener)
    }

    override fun assign(partitions: Collection<CordaTopicPartition>) {
        consumer.assign(partitions.toTopicPartitions(config.topicPrefix))
    }

    override fun assignment(): Set<CordaTopicPartition> =
        consumer.assignment().mapTo(HashSet()) { it.toCordaTopicPartition(config.topicPrefix) }

    override fun beginningOffsets(
        partitions: Collection<CordaTopicPartition>,
    ): Map<CordaTopicPartition, Long> {
        val partitionMap = consumer.beginningOffsets(partitions.toTopicPartitions(config.topicPrefix))
        return partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
    }

    override fun endOffsets(
        partitions: Collection<CordaTopicPartition>,
    ): Map<CordaTopicPartition, Long> {
        val partitionMap = consumer.endOffsets(partitions.toTopicPartitions(config.topicPrefix))
        return partitionMap.mapKeys { it.key.toCordaTopicPartition(config.topicPrefix) }
    }

    override fun poll(userFunction: (CordaConsumerRecord<K, V>) -> Unit) {
        processor.poll { consumer ->
            userFunction(consumer.singleConsumerRecord.toCordaConsumerRecord(config.topicPrefix))
        }
    }

    override fun pollAndProduceMany(userFunction: (CordaConsumerRecord<K, V>) -> List<CordaProducerRecord<*, *>>) {
        processor.pollAndProduceMany { consumer ->
            userFunction(consumer.singleConsumerRecord.toCordaConsumerRecord(config.topicPrefix))
                .map { it.toKafkaRecord(config.topicPrefix) }
        }
    }

    override fun close() {
        try {
            processor.close()
            consumer.close()
        } catch (ex: Exception) {
            log.error("CordaKafkaParallelProcessor failed to close from group ${config.group}.", ex)
        }
    }
}
