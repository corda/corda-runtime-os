package net.corda.messaging.db.util

import net.corda.messaging.db.persistence.DbSchema

class DbUtils {

    companion object {
        val createTopicRecordsTableStmt = "CREATE TABLE ${DbSchema.RecordsTable.TABLE_NAME} " +
                "(${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME} INT, " +
                "${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME} BIGINT, ${DbSchema.RecordsTable.RECORD_KEY_COLUMN_NAME} VARCHAR, " +
                "${DbSchema.RecordsTable.RECORD_VALUE_COLUMN_NAME} VARCHAR, ${DbSchema.RecordsTable.RECORD_TIMESTAMP_COLUMN_NAME} TIMESTAMP, " +
                "PRIMARY KEY (${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME}, ${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME}));"

        val createOffsetsTableStmt = "CREATE TABLE ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME} VARCHAR, " +
                "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} INT, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME} BIGINT, " +
                "PRIMARY KEY (${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME}));"

        val createTopicsTableStmt = "CREATE TABLE ${DbSchema.TopicsTable.TABLE_NAME} " +
                "(${DbSchema.TopicsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME} INT, PRIMARY KEY (${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}))"

    }

}