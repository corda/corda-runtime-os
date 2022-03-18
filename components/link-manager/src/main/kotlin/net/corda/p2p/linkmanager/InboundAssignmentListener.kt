package net.corda.p2p.linkmanager

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.p2p.SessionPartitions
import net.corda.schema.Schemas
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class InboundAssignmentListener(
    coordinatorFactory: LifecycleCoordinatorFactory,
    publisherFactory: PublisherFactory,
    configuration: SmartConfig
): PartitionAssignmentListener, LifecycleWithDominoTile {

    private val publisher = PublisherWithDominoLogic(
        publisherFactory,
        coordinatorFactory,
        PublisherConfig(CLIENT_ID),
        configuration,
    )

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        createResources = ::createResources,
        managedChildren = listOf(publisher.dominoTile),
        dependentChildren = listOf(publisher.dominoTile)
    )

    companion object {
        private const val CLIENT_ID = "session_partition_writer"
    }

    private val lock = ReentrantReadWriteLock()
    private val topicToPartition = mutableSetOf<Int>()
    private var firstAssignment = true

    private val future: CompletableFuture<Unit> = CompletableFuture()
    private val sessionIds = ConcurrentHashMap.newKeySet<String>()

    override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            val removed = topicPartitions.filter { it.first == Schemas.P2P.LINK_IN_TOPIC }.map { it.second }.toSet()
            topicToPartition.removeAll(removed)
        }
        val records = sessionIds.map { sessionId ->
            Record(Schemas.P2P.SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(topicToPartition.toList()))
        }
        if (records.isNotEmpty() && topicToPartition.isNotEmpty()) {
            publisher.publish(records)
        }
    }

    override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
        lock.write {
            if (firstAssignment) {
                firstAssignment = false
                future.complete(Unit)
            }
            val assigned = topicPartitions.filter { it.first == Schemas.P2P.LINK_IN_TOPIC }.map { it.second }.toSet()
            topicToPartition.addAll(assigned)
        }
        val records = sessionIds.map { sessionId ->
            Record(Schemas.P2P.SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(topicToPartition.toList()))
        }
        if (records.isNotEmpty() && topicToPartition.isNotEmpty()) {
            publisher.publish(records)
        }
    }

    fun addSessionsAndGetRecords(newSessionIds: Set<String>) : List<Record<String, SessionPartitions>> {
        return lock.read {
            sessionIds.addAll(newSessionIds)
            if (topicToPartition.isEmpty()) {
                return emptyList()
            }
            newSessionIds.map { sessionId ->
                Record(Schemas.P2P.SESSION_OUT_PARTITIONS, sessionId, SessionPartitions(topicToPartition.toList()))
            }
        }
    }

    fun sessionRemoved(sessionId: String) {
        sessionIds.remove(sessionId)
    }

    fun removeAllSessions() {
        sessionIds.clear()
    }

    private fun createResources(@Suppress("UNUSED_PARAMETER") resourcesHolder: ResourcesHolder): CompletableFuture<Unit> {
        return future
    }
}
