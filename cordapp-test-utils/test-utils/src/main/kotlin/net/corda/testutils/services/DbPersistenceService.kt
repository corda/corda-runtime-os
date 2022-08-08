package net.corda.testutils.services

import net.corda.testutils.internal.JpaPersistenceUnitInfo
import net.corda.testutils.internal.cast
import net.corda.testutils.tools.sandboxName
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import org.hibernate.cfg.AvailableSettings.DIALECT
import org.hibernate.cfg.AvailableSettings.HBM2DDL_AUTO
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_DRIVER
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_PASSWORD
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_URL
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_USER
import org.hibernate.dialect.HSQLDialect
import org.hibernate.jpa.HibernatePersistenceProvider
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class DbPersistenceService(x500 : MemberX500Name) : PersistenceService {

    private val emf = createEntityManagerFactory(x500)
    companion object {
        fun createEntityManagerFactory(x500 : MemberX500Name): EntityManagerFactory {
            return HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(
                    JpaPersistenceUnitInfo(),
                    mapOf(
                        JPA_JDBC_DRIVER to "org.hsqldb.jdbcDriver",
                        JPA_JDBC_URL to "jdbc:hsqldb:mem:${x500.sandboxName}",
                        JPA_JDBC_USER to "admin",
                        JPA_JDBC_PASSWORD to "",
                        DIALECT to HSQLDialect::class.java.name,
                        HBM2DDL_AUTO to "create",
                    )
                )
        }
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T? {
        return emf.createEntityManager().find(entityClass, primaryKey)
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T> {
        val em = emf.createEntityManager()
        return primaryKeys.map { em.find(entityClass, it) }
    }

    override fun <T : Any> findAll(entityClass: Class<T>): PagedQuery<T> {
        val query = emf.createEntityManager().createQuery("SELECT e FROM ${entityClass.simpleName} e")
        val result = cast<List<T>>(query.resultList)
            ?: throw java.lang.IllegalArgumentException("The result of the query was not an $entityClass")

        return object : PagedQuery<T> {
            override fun execute(): List<T>  = result

            override fun setLimit(limit: Int): PagedQuery<T> { TODO("Not yet implemented") }

            override fun setOffset(offset: Int): PagedQuery<T> { TODO("Not yet implemented") }
        }
    }

    override fun <T : Any> merge(entity: T): T? {
        emf.transaction {
            return it.merge(entity)
        }
    }

    override fun <T : Any> merge(entities: List<T>): List<T> {
        emf.transaction { em ->
            return entities.map { em.merge(it) }
        }
    }

    override fun persist(entity: Any) {
        emf.transaction {
            it.persist(entity)
        }
    }

    override fun persist(entities: List<Any>) {
        emf.transaction { em ->
            entities.forEach { em.persist(it) }
        }
    }

    override fun <T : Any> query(queryName: String, entityClass: Class<T>): ParameterisedQuery<T> {
        TODO("Not yet implemented")
    }

    override fun remove(entity: Any) {
        emf.transaction {
            it.remove(if (it.contains(entity)) { entity } else { it.merge(entity) })
        }
    }

    override fun remove(entities: List<Any>) {
        emf.transaction {
            entities.forEach { entity ->
                it.remove(if (it.contains(entity)) { entity } else { it.merge(entity) })
            }
        }
    }
}

inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R {

    val em = this.createEntityManager()
    val t = em.transaction
    t.begin()
    return try {
        block(em)
    } catch (e: Exception) {
        t.setRollbackOnly()
        throw e
    } finally {
        if (!t.rollbackOnly) {
            t.commit()
        } else {
            t.rollback()
        }
    }
}