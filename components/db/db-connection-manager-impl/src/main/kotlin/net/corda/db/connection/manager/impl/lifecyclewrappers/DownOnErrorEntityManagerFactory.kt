package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.lifecycle.LifecycleCoordinator
import javax.persistence.Cache
import javax.persistence.EntityGraph
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceUnitUtil
import javax.persistence.Query
import javax.persistence.SynchronizationType
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.metamodel.Metamodel

class DownOnErrorEntityManagerFactory(
    private val lifecycleCoordinator: LifecycleCoordinator,
    private val entityManagerFactory: EntityManagerFactory
) : EntityManagerFactory {
    override fun createEntityManager(): EntityManager = lifecycleCoordinator.downOnError { DownOnErrorEntityManager(
        lifecycleCoordinator,
        entityManagerFactory.createEntityManager()
    ) }

    override fun createEntityManager(map: MutableMap<Any?, Any?>?): EntityManager =
        lifecycleCoordinator.downOnError { DownOnErrorEntityManager(
            lifecycleCoordinator,
            entityManagerFactory.createEntityManager(map)
        ) }

    override fun createEntityManager(synchronizationType: SynchronizationType?): EntityManager =
        lifecycleCoordinator.downOnError { DownOnErrorEntityManager(
            lifecycleCoordinator,
            entityManagerFactory.createEntityManager(synchronizationType)
        ) }

    override fun createEntityManager(
        synchronizationType: SynchronizationType?,
        map: MutableMap<Any?, Any?>?
    ): EntityManager = lifecycleCoordinator.downOnError { DownOnErrorEntityManager(
        lifecycleCoordinator,
        entityManagerFactory.createEntityManager(synchronizationType, map)
    ) }

    override fun getCriteriaBuilder(): CriteriaBuilder =
        lifecycleCoordinator.downOnError { entityManagerFactory.criteriaBuilder }

    override fun getMetamodel(): Metamodel = lifecycleCoordinator.downOnError { entityManagerFactory.metamodel }

    override fun isOpen(): Boolean = lifecycleCoordinator.downOnError { entityManagerFactory.isOpen }

    override fun close() = lifecycleCoordinator.downOnError { entityManagerFactory.close() }

    override fun getProperties(): MutableMap<String, Any> =
        lifecycleCoordinator.downOnError { entityManagerFactory.properties }

    override fun getCache(): Cache = lifecycleCoordinator.downOnError { entityManagerFactory.cache }

    override fun getPersistenceUnitUtil(): PersistenceUnitUtil =
        lifecycleCoordinator.downOnError { entityManagerFactory.persistenceUnitUtil }

    override fun addNamedQuery(name: String?, query: Query?) =
        lifecycleCoordinator.downOnError { entityManagerFactory.addNamedQuery(name, query) }

    override fun <T : Any?> unwrap(cls: Class<T>?): T =
        lifecycleCoordinator.downOnError { entityManagerFactory.unwrap(cls) }

    override fun <T : Any?> addNamedEntityGraph(graphName: String?, entityGraph: EntityGraph<T>?) =
        lifecycleCoordinator.downOnError { entityManagerFactory.addNamedEntityGraph(graphName, entityGraph) }

}