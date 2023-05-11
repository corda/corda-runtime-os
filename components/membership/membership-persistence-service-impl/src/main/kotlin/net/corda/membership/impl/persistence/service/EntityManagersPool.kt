package net.corda.membership.impl.persistence.service

import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.utilities.minutes
import net.corda.utilities.time.Clock
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.time.Duration
import java.time.Instant
import java.util.Deque
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
    private data class EntityManagerInfo(
        val entityManagerFactory: EntityManagerFactory,
        val added: Instant,
    )
    private val pools = ConcurrentHashMap<ShortHash, Deque<EntityManagerInfo>>()

    fun <R> getEntityManagerInfo(holdingIdentityShortHash: ShortHash, block: (EntityManagerFactory) -> R): R {
        val em = pools[holdingIdentityShortHash]
            ?.pollFirst()
            ?.entityManagerFactory ?: createEntityManagerFactory(holdingIdentityShortHash)
        return try {
            block(em)
        } finally {
            pools.computeIfAbsent(holdingIdentityShortHash) {
                ConcurrentLinkedDeque()
            }.addFirst(
                EntityManagerInfo(
                    entityManagerFactory = em,
                    added = clock.instant(),
                ),
            )
        }
    }

    init {
        scheduler.scheduleAtFixedRate(::cleanup, 10, 10, TimeUnit.SECONDS)
    }

    private fun createEntityManagerFactory(holdingIdentityShortHash: ShortHash): EntityManagerFactory {
        val virtualNodeInfo = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)
            ?: throw MembershipPersistenceException(
                "Virtual node info can't be retrieved for " +
                    "holding identity ID $holdingIdentityShortHash",
            )

        return dbConnectionManager.createEntityManagerFactory(
            connectionId = virtualNodeInfo.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw MembershipPersistenceException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered.",
                ),
        )
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
