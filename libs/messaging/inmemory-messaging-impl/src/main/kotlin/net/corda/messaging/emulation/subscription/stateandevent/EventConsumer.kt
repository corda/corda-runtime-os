package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

@Suppress("EqualsWithHashCodeExist")
internal class EventConsumer<K : Any, E : Any>(
    private val subscription: EventSubscription<K, *, E>
) : Consumer {
    override val groupName = subscription.subscription.subscriptionConfig.groupName
    override val topicName = subscription.subscription.subscriptionConfig.eventTopic
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val commitStrategy = CommitStrategy.COMMIT_AFTER_PROCESSING
    override val partitionStrategy = PartitionStrategy.DIVIDE_PARTITIONS
    override val partitionAssignmentListener = object : PartitionAssignmentListener {
        override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
            subscription.subscription.topicService.manualUnAssignPartitions(
                subscription.subscription.stateSubscription.consumer,
                topicPartitions.map { it.second }
            )
        }

        override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
            subscription.subscription.topicService.manualAssignPartitions(
                subscription.subscription.stateSubscription.consumer,
                topicPartitions.map { it.second }
            )
        }
    }

    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.processEvents(records)
    }
}
