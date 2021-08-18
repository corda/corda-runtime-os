package net.corda.messaging.emulation.topic.model

import net.corda.messaging.api.subscription.PartitionAssignmentListener

interface ConsumerDefinitions {
    val groupName: String
    val topicName: String
    val offsetStrategy: OffsetStrategy
    val partitionStrategy: PartitionStrategy
    val partitionAssignmentListener: PartitionAssignmentListener?
    fun handleRecords(records: Collection<RecordMetadata>)
}
