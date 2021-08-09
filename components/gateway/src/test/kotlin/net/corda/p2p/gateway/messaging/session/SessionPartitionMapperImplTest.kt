package net.corda.p2p.gateway.messaging.session

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.SessionPartitions
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import net.corda.v5.base.util.uncheckedCast
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock

class SessionPartitionMapperImplTest {

    private var processor: CompactedProcessor<String, SessionPartitions>? = null

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), any<CompactedProcessor<String, SessionPartitions>>(), any()) } doAnswer {
            processor = uncheckedCast(it.arguments[1])
            mock()
        }
    }

    @Test
    fun `session partition mapping is calculated successfully`() {
        val partitionsMapping = mapOf(
            "1" to SessionPartitions(listOf(1, 2)),
            "2" to SessionPartitions(listOf(3, 4))
        )

        val sessionPartitionMapper = SessionPartitionMapperImpl(subscriptionFactory)
        sessionPartitionMapper.start()

        processor!!.onSnapshot(partitionsMapping)

        assertThat(sessionPartitionMapper.getPartitions("1")).isEqualTo(listOf(1, 2))
        assertThat(sessionPartitionMapper.getPartitions("2")).isEqualTo(listOf(3, 4))
        assertThat(sessionPartitionMapper.getPartitions("3")).isNull()

        val newRecord = Record(SESSION_OUT_PARTITIONS, "3", SessionPartitions(listOf(5, 6)))
        processor?.onNext(newRecord, null, partitionsMapping + (newRecord.key to newRecord.value!!))
        assertThat(sessionPartitionMapper.getPartitions("3")).isEqualTo(listOf(5, 6))
    }

}