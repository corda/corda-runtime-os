package net.corda.messaging.db.schema

class Schema {
    class TableNames {
        companion object {
            const val TOPIC_TABLE_PREFIX = "topic_"
            const val OFFSET_TABLE_PREFIX = "offset_"
        }
    }

    class TopicTable {
        companion object {
            const val OFFSET_COLUMN_NAME = "message_offset"
            const val KEY_COLUMN_NAME = "key"
            const val MESSAGE_PAYLOAD_COLUMN_NAME = "payload"
        }
    }

    class OffsetTable {
        companion object {
            const val COMMITTED_OFFSET_COLUMN_NAME = "committed_offset"
        }
    }
}