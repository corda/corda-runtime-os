package net.corda.testutils.services

import net.corda.testutils.internal.JpaPersistenceUnitInfo
import net.corda.testutils.internal.cast
import net.corda.testutils.tools.sandboxName
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
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

class DbPersistenceService(member : MemberX500Name) : CloseablePersistenceService {

    private val emf = createEntityManagerFactory(member)

    companion object {
        fun createEntityManagerFactory(member: MemberX500Name): EntityManagerFactory {
            return HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(
                    JpaPersistenceUnitInfo(),
                    mapOf(
                        JPA_JDBC_DRIVER to "org.hsqldb.jdbcDriver",
                        JPA_JDBC_URL to "jdbc:hsqldb:mem:${member.sandboxName};shutdown=true",
                        JPA_JDBC_USER to "admin",
                        JPA_JDBC_PASSWORD to "",
                        DIALECT to HSQLDialect::class.java.name,
                        HBM2DDL_AUTO to "create",
                    )
                )
        }
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T? {
        return emf.guard {
            it.find(entityClass, primaryKey)
        }
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T> {
        emf.guard {
            return primaryKeys.map { pk -> it.find(entityClass, pk) }
        }
    }

    override fun <T : Any> findAll(entityClass: Class<T>): PagedQuery<T> {
        emf.guard {
            val query = it.createQuery("SELECT e FROM ${entityClass.simpleName} e")
            val result = cast<List<T>>(query.resultList)
                ?: throw java.lang.IllegalArgumentException("The result of the query was not an $entityClass")

            return object : PagedQuery<T> {
                override fun execute(): List<T>  = result

                override fun setLimit(limit: Int): PagedQuery<T> { TODO("Not yet implemented") }

                override fun setOffset(offset: Int): PagedQuery<T> { TODO("Not yet implemented") }
            }
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

    override fun close() {
        emf.close()
    }
}

inline fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R {
    this.createEntityManager().use {
        val t = it.transaction
        t.begin()
        return try {
            block(it)
        } catch (e: Exception) {
            t.setRollbackOnly()
            throw CordaPersistenceException(e.message ?: "Error in persistence", e)
        } finally {
            if (!t.rollbackOnly) {
                t.commit()
            } else {
                t.rollback()
            }
        }
    }
}

inline fun <R> EntityManagerFactory.guard(block: (EntityManager) -> R): R {
    this.createEntityManager().use {
        return try {
            block(it)
        } catch (e: Exception) {
            throw CordaPersistenceException(e.message ?: "Error in persistence", e)
        }
    }
}

inline fun <R> EntityManager.use(block: (EntityManager) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}