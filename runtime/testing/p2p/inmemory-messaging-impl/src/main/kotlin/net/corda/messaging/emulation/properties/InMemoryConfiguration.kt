package net.corda.messaging.emulation.properties

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl

class InMemoryConfiguration {
    companion object {
        const val NAMED_TOPIC_PREFIX = "topics.named"
        const val DEFAULT_TOPIC_PREFIX = "topics.default"
        const val NAMED_SUBSCRIPTION_PREFIX = "subscriptions.named"
        const val DEFAULT_SUBSCRIPTION_PREFIX = "subscriptions.default"
        const val TOPICS_PARTITIONS_MAX_SIZE = "partitions.max.size"
        const val TOPICS_MAX_POLL_SIZE = "max.poll.size"
        const val PARTITIONS_COUNT = "partitions.count"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "consumer.thread.stop.timeout"
    }
    // TODO - replace with config service injection
    private val config: Config = let {
        val fallBack = ConfigFactory.load(
            TopicServiceImpl::class.java.classLoader,
            "inMemDefaults"
        )
        ConfigFactory.load("tmpInMemDefaults").withFallback(fallBack)
    }
    private val defaultTopicPartitionsMaxSize = config.getInt("$DEFAULT_TOPIC_PREFIX.$TOPICS_PARTITIONS_MAX_SIZE")
    private val defaultNumberOfPartitions = config.getInt("$DEFAULT_TOPIC_PREFIX.$PARTITIONS_COUNT")
    private val defaultMaxPollSize = config.getInt("$DEFAULT_SUBSCRIPTION_PREFIX.$TOPICS_MAX_POLL_SIZE")
    private val defaultConsumerThreadStopTimeout = config.getDuration("$DEFAULT_SUBSCRIPTION_PREFIX.$CONSUMER_THREAD_STOP_TIMEOUT")

    fun topicConfiguration(topicName: String): TopicConfiguration {
        val topicPath = "$NAMED_TOPIC_PREFIX.$topicName"
        val maxSize = if (config.hasPath("$topicPath.$TOPICS_PARTITIONS_MAX_SIZE")) {
            config.getInt("$topicPath.$TOPICS_PARTITIONS_MAX_SIZE")
        } else {
            defaultTopicPartitionsMaxSize
        }
        val numberOfPartitions = if (config.hasPath("$topicPath.$PARTITIONS_COUNT")) {
            config.getInt("$topicPath.$PARTITIONS_COUNT")
        } else {
            defaultNumberOfPartitions
        }
        return TopicConfiguration(
            partitionCount = numberOfPartitions,
            maxPartitionSize = maxSize,
        )
    }

    fun subscriptionConfiguration(groupName: String): SubscriptionConfiguration {
        val subscriptionPath = "$NAMED_SUBSCRIPTION_PREFIX.$groupName"
        val pollSize = if (config.hasPath("$subscriptionPath.$TOPICS_MAX_POLL_SIZE")) {
            config.getInt("$subscriptionPath.$TOPICS_MAX_POLL_SIZE")
        } else {
            defaultMaxPollSize
        }
        val threadStopTimeout = if (config.hasPath("$subscriptionPath.$CONSUMER_THREAD_STOP_TIMEOUT")) {
            config.getDuration("$subscriptionPath.$CONSUMER_THREAD_STOP_TIMEOUT")
        } else {
            defaultConsumerThreadStopTimeout
        }
        return SubscriptionConfiguration(
            maxPollSize = pollSize,
            threadStopTimeout = threadStopTimeout,
        )
    }
}
