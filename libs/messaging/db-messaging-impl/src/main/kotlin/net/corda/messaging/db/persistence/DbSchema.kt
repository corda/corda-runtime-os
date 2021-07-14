package net.corda.messaging.db.persistence

class DbSchema {
    class RecordsTable {
        companion object {
            const val TABLE_NAME = "topic_records"

            const val TOPIC_COLUMN_NAME = "topic"
            const val PARTITION_COLUMN_NAME = "partition_no"
            const val RECORD_OFFSET_COLUMN_NAME = "record_offset"
            const val RECORD_KEY_COLUMN_NAME = "record_key"
            const val RECORD_VALUE_COLUMN_NAME = "record_value"
            const val RECORD_TIMESTAMP_COLUMN_NAME = "record_timestamp"
        }
    }

    class CommittedOffsetsTable {
        companion object {
            const val TABLE_NAME = "topic_consumer_offsets"

            const val TOPIC_COLUMN_NAME = "topic"
            const val CONSUMER_GROUP_COLUMN_NAME = "consumer_group_name"
            const val PARTITION_COLUMN_NAME = "partition_no"
            const val COMMITTED_OFFSET_COLUMN_NAME = "committed_offset"
        }
    }

    class TopicsTable {
        companion object {
            const val TABLE_NAME = "topics"

            const val TOPIC_COLUMN_NAME = "topic_name"
            const val PARTITIONS_COLUMN_NAME = "partitions_number"
        }
    }

}