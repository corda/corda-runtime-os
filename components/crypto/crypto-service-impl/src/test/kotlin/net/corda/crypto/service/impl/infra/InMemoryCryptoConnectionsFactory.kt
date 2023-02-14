package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction


/** An in-memory implementation of CryptoConnetionsFactory
 *
 * Doesn't need lifecycle, but can optionally participate in lifecycle for test purposes.
 */
class InMemoryCryptoConnectionsFactory(
    val coordinatorFactory: LifecycleCoordinatorFactory? = null
) : CryptoConnectionsFactory {
    val keys: ConcurrentHashMap<String, WrappingKeyEntity> = ConcurrentHashMap()

    val entityTransaction: EntityTransaction = mock()

    val entityManager = mock<EntityManager> {
        on { persist(any()) }.thenAnswer {
            val wrappingKeyEntity = it.arguments.first() as WrappingKeyEntity
            keys[wrappingKeyEntity.alias] = wrappingKeyEntity
            null
        }
        on { find<Any>(any(), any()) }.thenAnswer {
            val alias = it.arguments[1] as String
            keys[alias]
        }
        on { transaction } doReturn entityTransaction
    }

    val emf = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }

    override fun getEntityManagerFactory(tenantId: String): EntityManagerFactory = emf

    fun exists(alias: String): Boolean = keys.containsKey(alias)

    val lifecycleCoordinator = coordinatorFactory?.createCoordinator(
        LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator?.isRunning ?: true

    override fun start() {
        lifecycleCoordinator?.start()
    }

    override fun stop() {
        lifecycleCoordinator?.stop()
    }
}