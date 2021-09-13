package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.emulation.topic.model.CommitStrategy
import net.corda.messaging.emulation.topic.model.Consumer
import net.corda.messaging.emulation.topic.model.OffsetStrategy
import net.corda.messaging.emulation.topic.model.PartitionStrategy
import net.corda.messaging.emulation.topic.model.RecordMetadata

@Suppress("EqualsWithHashCodeExist")
internal class StatesConsumer<K : Any, S : Any>(
    private val subscription: StateSubscription<K, S>
) : Consumer {
    override val groupName = subscription.subscription.stateSubscriptionConfig.groupName
    override val topicName = subscription.subscription.stateSubscriptionConfig.eventTopic
    override val offsetStrategy = OffsetStrategy.EARLIEST
    override val commitStrategy = CommitStrategy.NO_COMMIT
    override val partitionStrategy = PartitionStrategy.MANUAL
    override val partitionAssignmentListener = subscription
    override fun handleRecords(records: Collection<RecordMetadata>) {
        subscription.gotStates(records)
    }
}
