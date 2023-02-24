package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.lifecycle.LifecycleCoordinator
import javax.persistence.EntityGraph
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.FlushModeType
import javax.persistence.LockModeType
import javax.persistence.Query
import javax.persistence.StoredProcedureQuery
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaDelete
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.CriteriaUpdate
import javax.persistence.metamodel.Metamodel

@Suppress("TooManyFunctions")
class DownOnErrorEntityManager(
    private val lifecycleCoordinator: LifecycleCoordinator,
    private val entityManager: EntityManager
) : EntityManager {
    override fun persist(entity: Any?) = lifecycleCoordinator.downOnError { entityManager.persist(entity) }

    override fun <T : Any?> merge(entity: T): T = lifecycleCoordinator.downOnError { entityManager.merge(entity) }

    override fun remove(entity: Any?) = lifecycleCoordinator.downOnError { entityManager.remove(entity) }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?): T =
        lifecycleCoordinator.downOnError { entityManager.find(entityClass, primaryKey) }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?, properties: MutableMap<String, Any>?): T =
        lifecycleCoordinator.downOnError { entityManager.find(entityClass, primaryKey, properties) }

    override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?, lockMode: LockModeType?): T =
        lifecycleCoordinator.downOnError { entityManager.find(entityClass, primaryKey, lockMode) }

    override fun <T : Any?> find(
        entityClass: Class<T>?,
        primaryKey: Any?,
        lockMode: LockModeType?,
        properties: MutableMap<String, Any>?
    ): T = lifecycleCoordinator.downOnError { entityManager.find(entityClass, primaryKey, lockMode, properties) }

    override fun <T : Any?> getReference(entityClass: Class<T>?, primaryKey: Any?): T =
        lifecycleCoordinator.downOnError { entityManager.getReference(entityClass, primaryKey) }

    override fun flush() = lifecycleCoordinator.downOnError { entityManager.flush() }

    override fun setFlushMode(flushMode: FlushModeType?) =
        lifecycleCoordinator.downOnError { entityManager.flushMode = flushMode }

    override fun getFlushMode(): FlushModeType = lifecycleCoordinator.downOnError { entityManager.flushMode }

    override fun lock(entity: Any?, lockMode: LockModeType?) =
        lifecycleCoordinator.downOnError { entityManager.lock(entity, lockMode) }

    override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) =
        lifecycleCoordinator.downOnError { entityManager.lock(entity, lockMode, properties) }

    override fun refresh(entity: Any?) = lifecycleCoordinator.downOnError { entityManager.refresh(entity) }

    override fun refresh(entity: Any?, properties: MutableMap<String, Any>?) =
        lifecycleCoordinator.downOnError { entityManager.refresh(entity, properties) }

    override fun refresh(entity: Any?, lockMode: LockModeType?) =
        lifecycleCoordinator.downOnError { entityManager.refresh(entity, lockMode) }

    override fun refresh(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) =
        lifecycleCoordinator.downOnError { entityManager.refresh(entity, lockMode, properties) }

    override fun clear() = lifecycleCoordinator.downOnError { entityManager.close() }

    override fun detach(entity: Any?) = lifecycleCoordinator.downOnError { entityManager.detach(entity) }

    override fun contains(entity: Any?): Boolean = lifecycleCoordinator.downOnError { entityManager.contains(entity) }

    override fun getLockMode(entity: Any?): LockModeType =
        lifecycleCoordinator.downOnError { entityManager.getLockMode(entity) }

    override fun setProperty(propertyName: String?, value: Any?) =
        lifecycleCoordinator.downOnError { entityManager.setProperty(propertyName, value) }

    override fun getProperties(): MutableMap<String, Any> =
        lifecycleCoordinator.downOnError { entityManager.properties }

    override fun createQuery(qlString: String?): Query =
        lifecycleCoordinator.downOnError { entityManager.createQuery(qlString) }

    override fun <T : Any?> createQuery(criteriaQuery: CriteriaQuery<T>?): TypedQuery<T> =
        lifecycleCoordinator.downOnError { entityManager.createQuery(criteriaQuery) }

    override fun createQuery(updateQuery: CriteriaUpdate<*>?): Query =
        lifecycleCoordinator.downOnError { entityManager.createQuery(updateQuery) }

    override fun createQuery(deleteQuery: CriteriaDelete<*>?): Query =
        lifecycleCoordinator.downOnError { entityManager.createQuery(deleteQuery) }

    override fun <T : Any?> createQuery(qlString: String?, resultClass: Class<T>?): TypedQuery<T> =
        lifecycleCoordinator.downOnError { entityManager.createQuery(qlString, resultClass) }

    override fun createNamedQuery(name: String?): Query =
        lifecycleCoordinator.downOnError { entityManager.createNamedQuery(name) }

    override fun <T : Any?> createNamedQuery(name: String?, resultClass: Class<T>?): TypedQuery<T> =
        lifecycleCoordinator.downOnError { entityManager.createNamedQuery(name, resultClass) }

    override fun createNativeQuery(sqlString: String?): Query =
        lifecycleCoordinator.downOnError { entityManager.createNativeQuery(sqlString) }

    override fun createNativeQuery(sqlString: String?, resultClass: Class<*>?): Query =
        lifecycleCoordinator.downOnError { entityManager.createNativeQuery(sqlString, resultClass) }

    override fun createNativeQuery(sqlString: String?, resultSetMapping: String?): Query =
        lifecycleCoordinator.downOnError { entityManager.createNativeQuery(sqlString, resultSetMapping) }

    override fun createNamedStoredProcedureQuery(name: String?): StoredProcedureQuery {
        return lifecycleCoordinator.downOnError { entityManager.createNamedStoredProcedureQuery(name) }
    }

    override fun createStoredProcedureQuery(procedureName: String?): StoredProcedureQuery =
        lifecycleCoordinator.downOnError { createStoredProcedureQuery(procedureName) }

    override fun createStoredProcedureQuery(
        procedureName: String?,
        vararg resultClasses: Class<*>?
    ): StoredProcedureQuery =
        lifecycleCoordinator.downOnError { entityManager.createStoredProcedureQuery(procedureName, *resultClasses) }

    override fun createStoredProcedureQuery(
        procedureName: String?,
        vararg resultSetMappings: String?
    ): StoredProcedureQuery =
        lifecycleCoordinator.downOnError { entityManager.createStoredProcedureQuery(procedureName, *resultSetMappings) }

    override fun joinTransaction() = lifecycleCoordinator.downOnError { entityManager.joinTransaction() }

    override fun isJoinedToTransaction(): Boolean =
        lifecycleCoordinator.downOnError { entityManager.isJoinedToTransaction }

    override fun <T : Any?> unwrap(cls: Class<T>?): T = lifecycleCoordinator.downOnError { entityManager.unwrap(cls) }

    override fun getDelegate(): Any = lifecycleCoordinator.downOnError { entityManager.delegate }

    override fun close() = lifecycleCoordinator.downOnError { entityManager.close() }

    override fun isOpen(): Boolean = lifecycleCoordinator.downOnError { entityManager.isOpen }

    override fun getTransaction(): EntityTransaction = lifecycleCoordinator.downOnError { entityManager.transaction }

    override fun getEntityManagerFactory(): EntityManagerFactory = lifecycleCoordinator.downOnError {
        DownOnErrorEntityManagerFactory(lifecycleCoordinator, entityManager.entityManagerFactory)
    }

    override fun getCriteriaBuilder(): CriteriaBuilder =
        lifecycleCoordinator.downOnError { entityManager.criteriaBuilder }

    override fun getMetamodel(): Metamodel = lifecycleCoordinator.downOnError { entityManager.metamodel }

    override fun <T : Any?> createEntityGraph(rootType: Class<T>?): EntityGraph<T> =
        lifecycleCoordinator.downOnError { entityManager.createEntityGraph(rootType) }

    override fun createEntityGraph(graphName: String?): EntityGraph<*> =
        lifecycleCoordinator.downOnError { entityManager.createEntityGraph(graphName) }

    override fun getEntityGraph(graphName: String?): EntityGraph<*> =
        lifecycleCoordinator.downOnError { entityManager.getEntityGraph(graphName) }

    override fun <T : Any?> getEntityGraphs(entityClass: Class<T>?): MutableList<EntityGraph<in T>> =
        lifecycleCoordinator.downOnError { entityManager.getEntityGraphs(entityClass) }
}