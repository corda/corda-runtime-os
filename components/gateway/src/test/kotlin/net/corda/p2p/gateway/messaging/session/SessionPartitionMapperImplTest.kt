package net.corda.p2p.gateway.messaging.session

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.SessionPartitions
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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SessionPartitionMapperImplTest {

    private var processor = argumentCaptor<CompactedProcessor<String, SessionPartitions>>()
    private val dominoTile = Mockito.mockConstruction(DominoTile::class.java)

    private val factory = mock<LifecycleCoordinatorFactory>()
    private val subscription = mock<CompactedSubscription<String, SessionPartitions>>()

    private val subscriptionFactory = mock<SubscriptionFactory> {
        on { createCompactedSubscription(any(), processor.capture(), any()) } doReturn subscription
    }
    private val resourcesHolder = mock<ResourcesHolder>()
    private val config = SmartConfigImpl.empty()

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
    }

    @Test
    fun `session partition mapping is calculated successfully`() {
        val partitionsMapping = mapOf(
            "1" to SessionPartitions(listOf(1, 2)),
            "2" to SessionPartitions(listOf(3, 4))
        )

        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config, 1)
        doReturn(true).whenever(dominoTile.constructed().last()).isRunning
        sessionPartitionMapper.createResources(mock())

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
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config, 1)
        sessionPartitionMapper.createResources(mock())

        assertThatThrownBy { sessionPartitionMapper.getPartitions(sessionId) }
            .isInstanceOf(IllegalStateException::class.java)

        doReturn(true).whenever(dominoTile.constructed().last()).isRunning

        assertThat(sessionPartitionMapper.getPartitions(sessionId)).isNull()

        doReturn(false).whenever(dominoTile.constructed().last()).isRunning

        assertThatThrownBy { sessionPartitionMapper.getPartitions(sessionId) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `createResources will start the subscription and add it to the resource holder`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config, 1)

        sessionPartitionMapper.createResources(resourcesHolder)

        verify(subscription).start()
        //TODOs : this will be refactored as part of CORE-3147
        //verify(resourcesHolder).keep(subscription)
    }

    @Test
    fun `onSnapshot will complete the resource created future`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config, 1)
        val future = sessionPartitionMapper.createResources(resourcesHolder)

        processor.firstValue.onSnapshot(emptyMap())
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `empty record will remove the partition`() {
        val sessionPartitionMapper = SessionPartitionMapperImpl(factory, subscriptionFactory, config, 1)
        doReturn(true).whenever(dominoTile.constructed().last()).isRunning
        sessionPartitionMapper.createResources(mock())
        processor.firstValue.onSnapshot(mapOf("session" to SessionPartitions(listOf(3))))

        processor.firstValue.onNext(Record(SESSION_OUT_PARTITIONS, "session", null), null, emptyMap())

        assertThat(sessionPartitionMapper.getPartitions("session")).isNull()
    }
}
