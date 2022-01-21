package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener


interface Consumer {
    val groupName: String
    val topicName: String
    val offsetStrategy: OffsetStrategy
    val commitStrategy: CommitStrategy
    val partitionStrategy: PartitionStrategy
    val partitionAssignmentListener: PartitionAssignmentListener?
    fun handleRecords(records: Collection<RecordMetadata>)
}
