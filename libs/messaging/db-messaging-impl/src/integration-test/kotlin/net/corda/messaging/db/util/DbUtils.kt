package net.corda.messaging.db.util

import net.corda.messaging.db.persistence.DbSchema

class DbUtils {

    companion object {
        val createTopicRecordsTableStmt = "CREATE TABLE ${DbSchema.RecordsTable.TABLE_NAME} " +
                "(${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME} INT, " +
                "${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME} BIGINT, ${DbSchema.RecordsTable.RECORD_KEY_COLUMN_NAME} VARCHAR, " +
                "${DbSchema.RecordsTable.RECORD_VALUE_COLUMN_NAME} VARCHAR, ${DbSchema.RecordsTable.RECORD_TIMESTAMP_COLUMN_NAME} TIMESTAMP, " +
                "PRIMARY KEY (${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME}, ${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME}));"

        val cleanupTopicRecordsTableStmt = "DELETE FROM ${DbSchema.RecordsTable.TABLE_NAME}"

        val deleteTopicRecordsTableStmt = "DROP TABLE ${DbSchema.RecordsTable.TABLE_NAME}"

        val createOffsetsTableStmt = "CREATE TABLE ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME} VARCHAR, " +
                "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} INT, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME} BIGINT, " +
                "${DbSchema.CommittedOffsetsTable.OFFSET_TIMESTAMP_COLUMN_NAME} TIMESTAMP, " +
                "PRIMARY KEY (${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME}));"

        val cleanupOffsetsTableStmt = "DELETE FROM ${DbSchema.CommittedOffsetsTable.TABLE_NAME}"

        val deleteOffsetsTableStmt = "DROP TABLE ${DbSchema.CommittedOffsetsTable.TABLE_NAME}"

        val createTopicsTableStmt = "CREATE TABLE ${DbSchema.TopicsTable.TABLE_NAME} " +
                "(${DbSchema.TopicsTable.TOPIC_COLUMN_NAME} VARCHAR, ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME} INT, PRIMARY KEY (${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}))"

        val cleanupTopicsTableStmt = "DELETE FROM ${DbSchema.TopicsTable.TABLE_NAME}"

        val deleteTopicsTableStmt = "DROP TABLE ${DbSchema.TopicsTable.TABLE_NAME}"

    }

    /**
     * Statements specific to SQL Server.
     */
    class SQLServer {
        companion object {
            val createTopicRecordsTableStmt = "CREATE TABLE ${DbSchema.RecordsTable.TABLE_NAME} " +
                    "(${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME} INT, " +
                    "${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME} BIGINT, ${DbSchema.RecordsTable.RECORD_KEY_COLUMN_NAME} VARBINARY(MAX), " +
                    "${DbSchema.RecordsTable.RECORD_VALUE_COLUMN_NAME} VARBINARY(MAX), ${DbSchema.RecordsTable.RECORD_TIMESTAMP_COLUMN_NAME} DATETIME, " +
                    "PRIMARY KEY (${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME}, ${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME}));"

            val createOffsetsTableStmt = "CREATE TABLE ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                    "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME} VARCHAR(200), " +
                    "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} INT, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME} BIGINT, " +
                    "${DbSchema.CommittedOffsetsTable.OFFSET_TIMESTAMP_COLUMN_NAME} DATETIME, " +
                    "PRIMARY KEY (${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME}));"

            val createTopicsTableStmt = "CREATE TABLE ${DbSchema.TopicsTable.TABLE_NAME} " +
                    "(${DbSchema.TopicsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME} INT, PRIMARY KEY (${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}))"
        }
    }

    /**
     * Statements specific to Oracle.
     */
    class Oracle {
        companion object {
            val createTopicRecordsTableStmt = "CREATE TABLE ${DbSchema.RecordsTable.TABLE_NAME} " +
                    "(${DbSchema.RecordsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME} INT, " +
                    "${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME} NUMBER, ${DbSchema.RecordsTable.RECORD_KEY_COLUMN_NAME} BLOB, " +
                    "${DbSchema.RecordsTable.RECORD_VALUE_COLUMN_NAME} BLOB, ${DbSchema.RecordsTable.RECORD_TIMESTAMP_COLUMN_NAME} TIMESTAMP, " +
                    "PRIMARY KEY (${DbSchema.RecordsTable.TOPIC_COLUMN_NAME}, ${DbSchema.RecordsTable.PARTITION_COLUMN_NAME}, ${DbSchema.RecordsTable.RECORD_OFFSET_COLUMN_NAME}))"

            val createOffsetsTableStmt = "CREATE TABLE ${DbSchema.CommittedOffsetsTable.TABLE_NAME} " +
                    "(${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME} VARCHAR(200), " +
                    "${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME} INT, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME} NUMBER, " +
                    "${DbSchema.CommittedOffsetsTable.OFFSET_TIMESTAMP_COLUMN_NAME} TIMESTAMP, " +
                    "PRIMARY KEY (${DbSchema.CommittedOffsetsTable.TOPIC_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.CONSUMER_GROUP_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.PARTITION_COLUMN_NAME}, ${DbSchema.CommittedOffsetsTable.COMMITTED_OFFSET_COLUMN_NAME}))"

            val createTopicsTableStmt = "CREATE TABLE ${DbSchema.TopicsTable.TABLE_NAME} " +
                    "(${DbSchema.TopicsTable.TOPIC_COLUMN_NAME} VARCHAR(200), ${DbSchema.TopicsTable.PARTITIONS_COLUMN_NAME} INT, PRIMARY KEY (${DbSchema.TopicsTable.TOPIC_COLUMN_NAME}))"
        }
    }

}