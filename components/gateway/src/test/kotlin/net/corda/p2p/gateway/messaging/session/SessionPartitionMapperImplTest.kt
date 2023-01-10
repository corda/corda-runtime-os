package net.corda.p2p.gateway.messaging.session

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.BlockingDominoTile
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.SubscriptionDominoTile
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.data.p2p.SessionPartitions
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SessionPartitionMapperImplTest {

    private var processor = argumentCaptor<CompactedProcessor<String, SessionPartitions>>()
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java)
    private val subscriptionTile = Mockito.mockConstruction(SubscriptionDominoTile::class.java) { mock, context ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> CompactedSubscription<String, SessionPartitions>)).invoke()
    }
    private val blockingTile = Mockito.mockConstruction(BlockingDominoTile::class.java) { mock, _ ->
        whenever(mock.toNamedLifecycle()).thenReturn(mock())
    }

    private val factory = mock<LifecycleCoordinatorFactory>()
    private val subscription = mock<CompactedSubscription<String, SessionPartitions>>()

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), any()) } doReturn subscription
    }
    private val config = SmartConfigImpl.empty()

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        subscriptionTile.close()
        blockingTile.close()
    }

    @Test
    fun `session partition mapping is calculated successfully`() {
        val partitionsMapping = mapOf(
            "1" to SessionPartitions(listOf(1, 2)),
            "2" to SessionPartitions(listOf(3, 4))
        )

        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)
        doReturn(true).whenever(dominoTile.constructed().last()).isRunning

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

        doReturn(true).whenever(dominoTile.constructed().last()).isRunning

        assertThat(sessionPartitionMapper.getPartitions(sessionId)).isNull()

        doReturn(false).whenever(dominoTile.constructed().last()).isRunning

        assertThatThrownBy { sessionPartitionMapper.getPartitions(sessionId) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `onSnapshot will complete the resource created future`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)

        processor.firstValue.onSnapshot(emptyMap())
        assertThat(sessionPartitionMapper.future.isDone).isTrue
        assertThat(sessionPartitionMapper.future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `empty record will remove the partition`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config)
        doReturn(true).whenever(dominoTile.constructed().last()).isRunning
        processor.firstValue.onSnapshot(mapOf("session" to SessionPartitions(listOf(3))))

        processor.firstValue.onNext(Record(SESSION_OUT_PARTITIONS, "session", null), null, emptyMap())

        assertThat(sessionPartitionMapper.getPartitions("session")).isNull()
    }
}
