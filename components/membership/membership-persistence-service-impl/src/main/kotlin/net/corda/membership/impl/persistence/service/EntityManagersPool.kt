package net.corda.membership.impl.persistence.service

import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.minutes
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.Deque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManagerFactory

internal class EntityManagersPool(
    private val clock: Clock,
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
) {
    companion object {
        val logger = LoggerFactory.getLogger(EntityManagersPool::class.java)
    }
    private data class EntityManagerInfo(
        val entityManagerFactory: EntityManagerFactory,
        val added: Instant,
    )
    private val pools = ConcurrentHashMap<ShortHash, Deque<EntityManagerInfo>>()

    fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: (EntityManagerFactory) -> R): R {
        val start = clock.instant()
        var last = start
        val id = UUID.randomUUID()
        var curr: Instant = clock.instant()
        logger.info(
            "DB investigation " +
                    "- fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: " +
                    "(EntityManagerFactory) -> R): R " +
                    "- 1 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
        )
        last = curr
        val em = pools[holdingIdentityShortHash]
            ?.pollFirst()
            ?.entityManagerFactory ?: createEntityManagerFactory(holdingIdentityShortHash)
        curr = clock.instant()
        logger.info(
            "DB investigation " +
                    "- fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: " +
                    "(EntityManagerFactory) -> R): R " +
                    "- 2 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
        )
        last = curr
        return try {
            block(em).also {
                curr = clock.instant()
                logger.info(
                    "DB investigation " +
                            "- fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: " +
                            "(EntityManagerFactory) -> R): R " +
                            "- 3 " +
                            "- $id " +
                            "- Current: ${curr.nano} " +
                            "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                            "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                            "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
                )
                last = curr
            }
        } finally {
            pools.computeIfAbsent(holdingIdentityShortHash) {
                ConcurrentLinkedDeque()
            }.addFirst(
                EntityManagerInfo(
                    entityManagerFactory = em,
                    added = clock.instant(),
                ),
            )
            curr = clock.instant()
            logger.info(
                "DB investigation " +
                        "- fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: " +
                        "(EntityManagerFactory) -> R): R " +
                        "- 4 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano }ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            logger.info(
                "DB investigation " +
                        "- fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: " +
                        "(EntityManagerFactory) -> R): R " +
                        "- total " +
                        "- $id " +
                        "- Since start: ${curr.minusNanos(start.nano.toLong()).nano}ns " +
                        "- Since start: ${curr.toEpochMilli() - start.toEpochMilli()}ms " +
                        "- Since start: ${curr.epochSecond - start.epochSecond}s"
            )
        }
    }

    init {
        scheduler.scheduleAtFixedRate(::cleanup, 10, 10, TimeUnit.SECONDS)
    }

    private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): EntityManagerFactory {
        val start = clock.instant()
        var last = start
        val id = UUID.randomUUID()
        var curr: Instant = clock.instant()
        logger.info(
            "DB investigation " +
                    "- private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): " +
                    "EntityManagerFactory " +
                    "- 1 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
        )
        last = curr
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw MembershipPersistenceException(
                "Virtual node info can't be retrieved for " +
                    "holding identity ID $holdingIdentityShortHash",
            )
        curr = clock.instant()
        logger.info(
            "DB investigation " +
                    "- private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): " +
                    "EntityManagerFactory " +
                    "- 2 " +
                    "- $id " +
                    "- Current: ${curr.nano} " +
                    "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano}ns " +
                    "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms " +
                    "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s "
        )
        last = curr
        return dbConnectionManager.createEntityManagerFactory(
            connectionId = virtualNodeInfo.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw MembershipPersistenceException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered.",
                ),
        ).also {
            curr = clock.instant()
            logger.info(
                "DB investigation " +
                        "- private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): " +
                        "EntityManagerFactory " +
                        "- 3 " +
                        "- $id " +
                        "- Current: ${curr.nano} " +
                        "- Since last checkpoint: ${curr.minusNanos(last.nano.toLong()).nano }ns" +
                        "- Since last checkpoint: ${curr.toEpochMilli() - last.toEpochMilli()}ms" +
                        "- Since last checkpoint: ${curr.epochSecond - last.epochSecond}s"
            )
            logger.info(
                "DB investigation " +
                        "- private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): " +
                        "EntityManagerFactory " +
                        "- total " +
                        "- $id " +
                        "- Since start: ${curr.minusNanos(start.nano.toLong()).nano}ns " +
                        "- Since start: ${curr.toEpochMilli() - start.toEpochMilli()}ms " +
                        "- Since start: ${curr.epochSecond - start.epochSecond}s"
            )
        }
    }

    private fun cleanup() {
        val now = clock.instant()
        pools.values.removeIf { pool ->
            pool.removeIf { em ->
                val time = Duration.between(em.added, now)
                if (time > 1.minutes) {
                    em.entityManagerFactory.close()
                    true
                } else {
                    false
                }
            }
            pool.isEmpty()
        }
    }
}
