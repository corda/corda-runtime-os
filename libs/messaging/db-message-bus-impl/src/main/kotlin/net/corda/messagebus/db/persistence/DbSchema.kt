package net.corda.messagebus.db.persistence

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
            const val VISIBLE_COLUMN_NAME = "visible"

            const val maxOffsetsStatement = """
                select $TOPIC_COLUMN_NAME, ${PARTITION_COLUMN_NAME}, max($RECORD_OFFSET_COLUMN_NAME)
                    from $TABLE_NAME, ${CommittedOffsetsTable.TABLE_NAME}
                    group by $TOPIC_COLUMN_NAME, $PARTITION_COLUMN_NAME
                """

            const val insertRecordStatement = """
                insert into $TABLE_NAME
                    ($TOPIC_COLUMN_NAME, $PARTITION_COLUMN_NAME,
                    $RECORD_OFFSET_COLUMN_NAME, $RECORD_KEY_COLUMN_NAME,
                    $RECORD_VALUE_COLUMN_NAME, $RECORD_TIMESTAMP_COLUMN_NAME,
                    $VISIBLE_COLUMN_NAME)
                    values (?, ?, ?, ?, ?, ?, ?)
                """

            const val updateRecordVisibility = """
                update $TABLE_NAME
                set $VISIBLE_COLUMN_NAME = true
                when $RECORD_OFFSET_COLUMN_NAME = ?
            """

            const val readRecordsStmt = """
                select ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME,
                    $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME
                    from $TABLE_NAME where
                    $TOPIC_COLUMN_NAME = ? and
                    $PARTITION_COLUMN_NAME = ? and
                    $RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ?
                    order by $RECORD_OFFSET_COLUMN_NAME asc
                    limit ?
                """

            const val readRecordsStmtForSQLServer = """
                select top (?) ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME,
                    $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME
                    from $TABLE_NAME where
                    $TOPIC_COLUMN_NAME = ? and
                    $PARTITION_COLUMN_NAME = ? and
                    $RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ?
                    order by $RECORD_OFFSET_COLUMN_NAME asc
                """

            const val readRecordsStmtForOracle = """
                select * from (
                    select ${PARTITION_COLUMN_NAME}, $RECORD_OFFSET_COLUMN_NAME,
                    $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME
                    from $TABLE_NAME where
                    $TOPIC_COLUMN_NAME = ? and
                    $PARTITION_COLUMN_NAME = ? and
                    $RECORD_OFFSET_COLUMN_NAME >= ? and $RECORD_OFFSET_COLUMN_NAME <= ?
                    order by $RECORD_OFFSET_COLUMN_NAME asc)
                    where ROWNUM <= ?
                """

            const val selectRecordByPartitionOffsetStmt = """
                select $RECORD_KEY_COLUMN_NAME, $RECORD_VALUE_COLUMN_NAME
                    from $TABLE_NAME where
                    $TOPIC_COLUMN_NAME = ? and
                    $PARTITION_COLUMN_NAME = ? and
                    $RECORD_OFFSET_COLUMN_NAME = ?
                """

            const val deleteRecordsStmt = """
                delete from $TABLE_NAME
                    where $TOPIC_COLUMN_NAME = ? and
                    $RECORD_TIMESTAMP_COLUMN_NAME < ?
                """
        }
    }

    class CommittedOffsetsTable {
        companion object {
            const val TABLE_NAME = "topic_consumer_offsets"

            const val TOPIC_COLUMN_NAME = "topic"
            const val CONSUMER_GROUP_COLUMN_NAME = "consumer_group_name"
            const val PARTITION_COLUMN_NAME = "partition_no"
            const val OFFSET_COLUMN_NAME = "offset"
            const val OFFSET_TIMESTAMP_COLUMN_NAME = "offset_timestamp"
            const val COMMITTED_COLUMN_NAME = "committed"

            const val commitOffsetStmt = """
                insert into $TABLE_NAME 
                    ($TOPIC_COLUMN_NAME, $CONSUMER_GROUP_COLUMN_NAME, $PARTITION_COLUMN_NAME, 
                    $OFFSET_COLUMN_NAME, $OFFSET_TIMESTAMP_COLUMN_NAME, $COMMITTED_COLUMN_NAME)
                    values (?, ?, ?, ?, ?, ?)
                """

            const val maxCommittedOffsetsStmt = """
                select $PARTITION_COLUMN_NAME, max($OFFSET_COLUMN_NAME)
                        from $TABLE_NAME
                        where $TOPIC_COLUMN_NAME = ? and
                        $CONSUMER_GROUP_COLUMN_NAME = ? and
                        $COMMITTED_COLUMN_NAME = true and
                        $PARTITION_COLUMN_NAME in [partitions_list]
                        group by $PARTITION_COLUMN_NAME
                """

            const val deleteOffsetsStmt = """
                delete from $TABLE_NAME
                    where $TOPIC_COLUMN_NAME = ? and
                    $OFFSET_TIMESTAMP_COLUMN_NAME < ?
                """

        }
    }

    class TopicsTable {
        companion object {
            const val TABLE_NAME = "topic"

            const val TOPIC_COLUMN_NAME = "topic_name"
            const val PARTITIONS_COLUMN_NAME = "partitions_number"

            const val readTopicsStmt = """
                select $TOPIC_COLUMN_NAME, $PARTITIONS_COLUMN_NAME
                    " from $TABLE_NAME
                """

            const val insertTopicStmt = """
                insert into $TABLE_NAME
                    ($TOPIC_COLUMN_NAME, $PARTITIONS_COLUMN_NAME)
                    values (?, ?)"
                """

        }
    }

}
