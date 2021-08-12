package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.subscription.PartitionAssignmentListener

interface Consumer {
    val groupName: String
    val topicName: String
    val offsetStrategy: OffsetStrategy
    val partitionAssignmentListener: PartitionAssignmentListener?
    fun handleRecords(records: Collection<RecordMetadata>)
}
