package net.corda.p2p.linkmanager

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.records.Record
import net.corda.p2p.SessionPartitions
import net.corda.schema.Schemas
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CompletableFuture

class InboundAssignmentListenerTest {

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private var createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)? = null
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, context ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.isRunning).doReturn(true)
        @Suppress("UNCHECKED_CAST")
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName(context.arguments()[0] as String, ""))
        @Suppress("UNCHECKED_CAST")
        createResources = context.arguments()[2] as ((resources: ResourcesHolder) -> CompletableFuture<Unit>)?
    }
    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        whenever(mock.isRunning).doReturn(true)
    }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
        publisherWithDominoLogic.close()
    }

    @Test
    fun `Partitions can be assigned and reassigned`() {
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory, mock(), mock())
        assertEquals(0, listener.addSessionsAndGetRecords(emptySet()).size)
        val assign1 = listOf(1, 3, 5)
        val firstAssignment = assign1.map { Schemas.P2P.LINK_IN_TOPIC to it }
        listener.onPartitionsAssigned(firstAssignment)
        val sessionIds = setOf("firstSession, secondSession")
        val expectedRecords = sessionIds.map { Record(Schemas.P2P.SESSION_OUT_PARTITIONS, it, SessionPartitions(assign1)) }
        val records = listener.addSessionsAndGetRecords(sessionIds)
        assertThat(records).containsExactlyInAnyOrderElementsOf(expectedRecords)
        verify(publisherWithDominoLogic.constructed().first(), never()).publish(any())

        val unAssignPartition = 1
        val newSession = "newSession"
        listener.onPartitionsUnassigned(listOf(Schemas.P2P.LINK_IN_TOPIC to unAssignPartition))
        val recordsOnUnassignment = sessionIds.map {
            Record(Schemas.P2P.SESSION_OUT_PARTITIONS, it, SessionPartitions(assign1 - unAssignPartition))
        }
        verify(publisherWithDominoLogic.constructed().first()).publish(recordsOnUnassignment)
        assertThat(listener.addSessionsAndGetRecords(setOf(newSession))).containsExactlyInAnyOrder(
            Record(Schemas.P2P.SESSION_OUT_PARTITIONS, newSession, SessionPartitions(assign1 - unAssignPartition)))
    }

    @Test
    fun `the future completes when partitions are assigned`() {
        val listener = InboundAssignmentListener(lifecycleCoordinatorFactory, mock(), mock())
        val readyFuture = createResources!!(mock())
        assertEquals(0, listener.addSessionsAndGetRecords(emptySet()).size)
        val assign1 = listOf(1, 3, 5)
        val firstAssignment = assign1.map { Schemas.P2P.LINK_IN_TOPIC to it }
        listener.onPartitionsAssigned(firstAssignment)
        assertThat(readyFuture.isDone).isTrue
    }
}