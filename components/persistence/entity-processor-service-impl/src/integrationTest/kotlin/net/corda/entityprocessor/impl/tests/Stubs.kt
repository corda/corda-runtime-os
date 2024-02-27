package net.corda.entityprocessor.impl.tests

import org.slf4j.LoggerFactory
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

/** Manual stubs to avoid Mockito in OSGi tests. */
object Stubs {
    @Suppress("TooManyFunctions")
    class EntityManagerStub : EntityManager {
        companion object {
            private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        }

        val persisted = mutableListOf<Any?>()

        override fun persist(entity: Any?) {
            logger.info("Stub persist $entity")
            persisted.add(entity)
        }

        override fun <T : Any?> merge(entity: T): T {
            TODO("Not yet implemented")
        }

        override fun remove(entity: Any?) {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> find(
            entityClass: Class<T>?,
            primaryKey: Any?,
            properties: MutableMap<String, Any>?,
        ): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> find(entityClass: Class<T>?, primaryKey: Any?, lockMode: LockModeType?): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> find(
            entityClass: Class<T>?,
            primaryKey: Any?,
            lockMode: LockModeType?,
            properties: MutableMap<String, Any>?,
        ): T {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> getReference(entityClass: Class<T>?, primaryKey: Any?): T {
            TODO("Not yet implemented")
        }

        override fun flush() {
            TODO("Not yet implemented")
        }

        override fun setFlushMode(flushMode: FlushModeType?) {
            TODO("Not yet implemented")
        }

        override fun getFlushMode(): FlushModeType {
            TODO("Not yet implemented")
        }

        override fun lock(entity: Any?, lockMode: LockModeType?) {
            TODO("Not yet implemented")
        }

        override fun lock(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
            TODO("Not yet implemented")
        }

        override fun refresh(entity: Any?) {
            TODO("Not yet implemented")
        }

        override fun refresh(entity: Any?, properties: MutableMap<String, Any>?) {
            TODO("Not yet implemented")
        }

        override fun refresh(entity: Any?, lockMode: LockModeType?) {
            TODO("Not yet implemented")
        }

        override fun refresh(entity: Any?, lockMode: LockModeType?, properties: MutableMap<String, Any>?) {
            TODO("Not yet implemented")
        }

        override fun clear() {
            TODO("Not yet implemented")
        }

        override fun detach(entity: Any?) {
            TODO("Not yet implemented")
        }

        override fun contains(entity: Any?): Boolean {
            TODO("Not yet implemented")
        }

        override fun getLockMode(entity: Any?): LockModeType {
            TODO("Not yet implemented")
        }

        override fun setProperty(propertyName: String?, value: Any?) {
            TODO("Not yet implemented")
        }

        override fun getProperties(): MutableMap<String, Any> {
            TODO("Not yet implemented")
        }

        override fun createQuery(qlString: String?): Query {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> createQuery(criteriaQuery: CriteriaQuery<T>?): TypedQuery<T> {
            TODO("Not yet implemented")
        }

        override fun createQuery(updateQuery: CriteriaUpdate<*>?): Query {
            TODO("Not yet implemented")
        }

        override fun createQuery(deleteQuery: CriteriaDelete<*>?): Query {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> createQuery(qlString: String?, resultClass: Class<T>?): TypedQuery<T> {
            TODO("Not yet implemented")
        }

        override fun createNamedQuery(name: String?): Query {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> createNamedQuery(name: String?, resultClass: Class<T>?): TypedQuery<T> {
            TODO("Not yet implemented")
        }

        override fun createNativeQuery(sqlString: String?): Query {
            TODO("Not yet implemented")
        }

        override fun createNativeQuery(sqlString: String?, resultClass: Class<*>?): Query {
            TODO("Not yet implemented")
        }

        override fun createNativeQuery(sqlString: String?, resultSetMapping: String?): Query {
            TODO("Not yet implemented")
        }

        override fun createNamedStoredProcedureQuery(name: String?): StoredProcedureQuery {
            TODO("Not yet implemented")
        }

        override fun createStoredProcedureQuery(procedureName: String?): StoredProcedureQuery {
            TODO("Not yet implemented")
        }

        override fun createStoredProcedureQuery(
            procedureName: String?,
            vararg resultClasses: Class<*>?,
        ): StoredProcedureQuery {
            TODO("Not yet implemented")
        }

        override fun createStoredProcedureQuery(
            procedureName: String?,
            vararg resultSetMappings: String?,
        ): StoredProcedureQuery {
            TODO("Not yet implemented")
        }

        override fun joinTransaction() {
            TODO("Not yet implemented")
        }

        override fun isJoinedToTransaction(): Boolean {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> unwrap(cls: Class<T>?): T {
            TODO("Not yet implemented")
        }

        override fun getDelegate(): Any {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }

        override fun isOpen(): Boolean {
            TODO("Not yet implemented")
        }

        override fun getTransaction(): EntityTransaction {
            TODO("Not yet implemented")
        }

        override fun getEntityManagerFactory(): EntityManagerFactory {
            TODO("Not yet implemented")
        }

        override fun getCriteriaBuilder(): CriteriaBuilder {
            TODO("Not yet implemented")
        }

        override fun getMetamodel(): Metamodel {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> createEntityGraph(rootType: Class<T>?): EntityGraph<T> {
            TODO("Not yet implemented")
        }

        override fun createEntityGraph(graphName: String?): EntityGraph<*> {
            TODO("Not yet implemented")
        }

        override fun getEntityGraph(graphName: String?): EntityGraph<*> {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> getEntityGraphs(entityClass: Class<T>?): MutableList<EntityGraph<in T>> {
            TODO("Not yet implemented")
        }

    }
}
