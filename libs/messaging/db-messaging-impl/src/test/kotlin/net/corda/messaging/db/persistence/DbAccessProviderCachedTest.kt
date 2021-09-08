package net.corda.messaging.db.persistence

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.verify
import java.lang.RuntimeException

class DbAccessProviderCachedTest {

    private val topic = "test.topic"
    private val partitions = 5
    private val consumerGroup = "test_consumer_group"

    private val records = listOf(
        RecordDbEntry(topic, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
        RecordDbEntry(topic, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
        RecordDbEntry(topic, 1, 3, "key-3".toByteArray(), "value-3".toByteArray()),
        RecordDbEntry(topic, 1, 4, "key-4".toByteArray(), "value-4".toByteArray()),
        RecordDbEntry(topic, 1, 5, "key-5".toByteArray(), "value-5".toByteArray())
    )

    private val dbAccessProviderImpl = mock(DBAccessProviderImpl::class.java).apply {
        `when`(getTopics()).thenReturn(mapOf(topic to partitions))
    }

    private val dbAccessProviderCached = DBAccessProviderCached(dbAccessProviderImpl, 100)

    @BeforeEach
    fun setup() {
        dbAccessProviderCached.start()
    }

    @AfterEach
    fun cleanup() {
        dbAccessProviderCached.stop()
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `when transaction for write records is not committed, records are not cached`() {
        @Suppress("UNCHECKED_CAST")
        `when`(dbAccessProviderImpl.writeRecords(anyOrNull(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = invocation.arguments[0] as List<RecordDbEntry>
                val postTxFn = invocation.arguments[1] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)
                try {
                    throw RuntimeException("boom!")
                } finally {
                    postTxFn(records, TransactionResult.ROLLED_BACK)
                }
            }

        var returnedTxResult: TransactionResult? = null
        var returnedRecords: List<RecordDbEntry> = emptyList()
        val postTxFn = { writtenRecords: List<RecordDbEntry>, txResult: TransactionResult ->
            returnedRecords = writtenRecords
            returnedTxResult = txResult
        }

        // exception propagated and post tx function invoked
        assertThatThrownBy { dbAccessProviderCached.writeRecords(records, postTxFn) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("boom!")
        assertThat(returnedTxResult).isEqualTo(TransactionResult.ROLLED_BACK)
        assertThat(returnedRecords).isEqualTo(records)
        // records were not written to the cache
        assertThat(dbAccessProviderCached.getCache().getAllEntries(topic, 1)).isEmpty()
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun `when transaction for write offsets and records is not committed, records are not cached`() {
        @Suppress("UNCHECKED_CAST")
        `when`(dbAccessProviderImpl.writeOffsetsAndRecordsAtomically(anyString(), anyString(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = invocation.arguments[3] as List<RecordDbEntry>
                val postTxFn = invocation.arguments[4] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)
                try {
                    throw RuntimeException("boom!")
                } finally {
                    postTxFn(records, TransactionResult.ROLLED_BACK)
                }
            }

        var returnedTxResult: TransactionResult? = null
        var returnedRecords: List<RecordDbEntry> = emptyList()
        val postTxFn = { writtenRecords: List<RecordDbEntry>, txResult: TransactionResult ->
            returnedRecords = writtenRecords
            returnedTxResult = txResult
        }

        // exception propagated and post tx function invoked
        assertThatThrownBy {
            dbAccessProviderCached.writeOffsetsAndRecordsAtomically(
                topic,
                consumerGroup, mapOf(3 to 1), records, postTxFn
            )
        }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("boom!")
        assertThat(returnedTxResult).isEqualTo(TransactionResult.ROLLED_BACK)
        assertThat(returnedRecords).isEqualTo(records)
        // records were not written to the cache
        assertThat(dbAccessProviderCached.getCache().getAllEntries(topic, 1)).isEmpty()
    }

    @Test
    fun `when transaction for write records is committed, records are cached`() {
        val persistedRecords = mutableListOf<RecordDbEntry>()
        @Suppress("UNCHECKED_CAST")
        `when`(dbAccessProviderImpl.writeRecords(anyOrNull(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = invocation.arguments[0] as List<RecordDbEntry>
                val postTxFn = invocation.arguments[1] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)
                persistedRecords.addAll(records)
                postTxFn(records, TransactionResult.COMMITTED)
            }

        var returnedTxResult: TransactionResult? = null
        var returnedRecords: List<RecordDbEntry> = emptyList()
        val postTxFn = { writtenRecords: List<RecordDbEntry>, txResult: TransactionResult ->
            returnedRecords = writtenRecords
            returnedTxResult = txResult
        }

        // records were written and post tx function invoked
        dbAccessProviderCached.writeRecords(records, postTxFn)
        assertThat(returnedTxResult).isEqualTo(TransactionResult.COMMITTED)
        assertThat(returnedRecords).isEqualTo(records)
        assertThat(persistedRecords).isEqualTo(records)
        // records were written to the cache
        assertThat(dbAccessProviderCached.getCache().getAllEntries(topic, 1).values).containsAll(records)
    }

    @Test
    fun `when transaction for write offsets and records is committed, records are cached`() {
        val persistedRecords = mutableListOf<RecordDbEntry>()
        @Suppress("UNCHECKED_CAST")
        `when`(dbAccessProviderImpl.writeOffsetsAndRecordsAtomically(anyString(), anyString(), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenAnswer { invocation ->
                val records = invocation.arguments[3] as List<RecordDbEntry>
                val postTxFn = invocation.arguments[4] as ((records: List<RecordDbEntry>, txResult: TransactionResult) -> Unit)
                persistedRecords.addAll(records)
                postTxFn(records, TransactionResult.COMMITTED)
            }

        var returnedTxResult: TransactionResult? = null
        var returnedRecords: List<RecordDbEntry> = emptyList()
        val postTxFn = { writtenRecords: List<RecordDbEntry>, txResult: TransactionResult ->
            returnedRecords = writtenRecords
            returnedTxResult = txResult
        }

        // records written and post tx function invoked
        dbAccessProviderCached.writeOffsetsAndRecordsAtomically(topic, consumerGroup, mapOf(3 to 1), records, postTxFn)
        assertThat(returnedTxResult).isEqualTo(TransactionResult.COMMITTED)
        assertThat(returnedRecords).isEqualTo(records)
        assertThat(persistedRecords).containsAll(records)
        // records were written to the cache
        assertThat(dbAccessProviderCached.getCache().getAllEntries(topic, 1).values).containsAll(records)
    }

    @Test
    fun `when records exist in cache, no query is performed on the database for the corresponding window`() {
        // records for partition 1 in DB
        val dbFetchWindows = listOf(FetchWindow(1, 1, 5, 5))
        `when`(dbAccessProviderImpl.readRecords(topic, dbFetchWindows)).thenReturn(records)
        // records for partition 2 in cache
        val cachedFetchWindows = listOf(FetchWindow(2, 1, 5, 5))
        val cachedRecords = (1L..5L).map { offset ->
            offset to RecordDbEntry(topic, 2, offset, "key-$offset".toByteArray(), "value-$offset".toByteArray())
        }.toMap()
        dbAccessProviderCached.getCache().getAllEntries(topic, 2).putAll(cachedRecords)

        val returnedRecords = dbAccessProviderCached.readRecords(topic, dbFetchWindows + cachedFetchWindows)

        assertThat(returnedRecords).containsAll(records)
        assertThat(returnedRecords).containsAll(cachedRecords.values)
        verify(dbAccessProviderImpl, times(1)).readRecords(topic, dbFetchWindows)
        verify(dbAccessProviderImpl, times(0)).readRecords(topic, cachedFetchWindows)
    }

    @Test
    fun `create topic adds the new topic to the cache`() {
        val newTopic = "test.topic-2"
        dbAccessProviderCached.createTopic(newTopic, 5)

        verify(dbAccessProviderImpl, times(1)).createTopic(newTopic, 5)
        assertThat(dbAccessProviderCached.getCache().getAllEntries(newTopic, 2)).isNotNull
    }
}
