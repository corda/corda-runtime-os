package net.corda.p2p.gateway.messaging.session

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.SessionPartitions
import net.corda.p2p.schema.Schema.Companion.SESSION_OUT_PARTITIONS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SessionPartitionMapperImplTest {

    private var processor = argumentCaptor<CompactedProcessor<String, SessionPartitions>>()
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val subscription = mock<CompactedSubscription<String, SessionPartitions>>()

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), any()) } doReturn subscription
    }
    private val config = SmartConfigImpl.empty()

    @Test
    fun `session partition mapping is calculated successfully`() {
        doReturn(LifecycleStatus.UP).whenever(coordinator).status
        val partitionsMapping = mapOf(
            "1" to SessionPartitions(listOf(1, 2)),
            "2" to SessionPartitions(listOf(3, 4))
        )

        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)
        sessionPartitionMapper.start()

        processor.firstValue.onSnapshot(partitionsMapping)

        assertThat(sessionPartitionMapper.getPartitions("1")).isEqualTo(listOf(1, 2))
        assertThat(sessionPartitionMapper.getPartitions("2")).isEqualTo(listOf(3, 4))
        assertThat(sessionPartitionMapper.getPartitions("3")).isNull()

        val newRecord = Record(SESSION_OUT_PARTITIONS, "3", SessionPartitions(listOf(5, 6)))
        processor.firstValue.onNext(newRecord, null, partitionsMapping + (newRecord.key to newRecord.value!!))
        assertThat(sessionPartitionMapper.getPartitions("3")).isEqualTo(listOf(5, 6))
    }

    @Test
    fun `getPartitions cannot be invoked, when component is not running`() {
        val sessionId = "test-session-id"
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)

        assertThatThrownBy { sessionPartitionMapper.getPartitions(sessionId) }
            .isInstanceOf(IllegalStateException::class.java)

        processor.firstValue.onSnapshot(emptyMap())

        assertThat(sessionPartitionMapper.getPartitions(sessionId)).isNull()

        sessionPartitionMapper.stop()

        assertThatThrownBy { sessionPartitionMapper.getPartitions(sessionId) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `createResources will start the subscription`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)

        sessionPartitionMapper.createResources()

        verify(subscription).start()
    }

    @Test
    fun `stop will stop the subscription`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)

        sessionPartitionMapper.createResources()
        sessionPartitionMapper.stop()

        verify(subscription).stop()
    }

    @Test
    fun `empty record will remove the partition`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)
        processor.firstValue.onSnapshot(mapOf("session" to SessionPartitions(listOf(3))))

        processor.firstValue.onNext(Record(SESSION_OUT_PARTITIONS, "session", null), null, emptyMap())

        assertThat(sessionPartitionMapper.getPartitions("session")).isNull()
    }
}
