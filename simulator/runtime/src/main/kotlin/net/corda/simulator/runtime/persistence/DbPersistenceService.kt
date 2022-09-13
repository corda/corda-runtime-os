package net.corda.simulator.runtime.persistence

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.simulator.runtime.utils.sandboxName
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterisedQuery
import net.corda.v5.base.types.MemberX500Name
import org.hibernate.Session
import org.hibernate.cfg.AvailableSettings.*
import org.hibernate.dialect.HSQLDialect
import org.hibernate.jpa.HibernatePersistenceProvider
import java.sql.Connection
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class DbPersistenceService(member : MemberX500Name) : CloseablePersistenceService {

    private val emf = createEntityManagerFactory(member)

    companion object {
        fun createEntityManagerFactory(member: MemberX500Name): EntityManagerFactory {
            val emf = HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(
                    JpaPersistenceUnitInfo(),
                    mapOf(
                        JPA_JDBC_DRIVER to "org.hsqldb.jdbcDriver",
                        JPA_JDBC_URL to "jdbc:hsqldb:mem:${member.sandboxName};shutdown=true",
                        JPA_JDBC_USER to "admin",
                        JPA_JDBC_PASSWORD to "",
                        DIALECT to HSQLDialect::class.java.name,
//                        HBM2DDL_AUTO to "create",
                        "hibernate.show_sql" to "true",
                        "hibernate.format_sql" to "true",
                    )
                )

            // NOTE: only need the connection here, creating EM is a bit wasteful but ok for now.
            //  alternative option would be to create a connection manually here.
            emf.createEntityManager().use { em ->
                em.unwrap(Session::class.java).doWork {
                     runMigrations(it)
                }
            }
            return emf
        }

        fun runMigrations(connection: Connection) {
            val cl1 = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        "simulator-${DbPersistenceService::javaClass.name}",
                        // TODO: constant in `components/virtual-node/virtual-node-write-service-impl/src/main/kotlin/net/corda/virtualnode/write/db/impl/writer/VirtualNodeDbChangeLog.kt`
                        //   should move into API repo.
                        listOf("migration/db.changelog-master.xml")
                    ),
                )
            )
            val lbm = LiquibaseSchemaMigratorImpl()
            lbm.updateDb(connection, cl1)
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
            val query = it.createQuery("FROM ${entityClass.simpleName} e")
            val result = query.resultList

            return object : PagedQuery<T> {

                override fun execute(): List<T>  {
                    @Suppress("UNCHECKED_CAST")
                    try { return result as List<T> }
                    catch(e: ClassCastException) {
                        throw java.lang.IllegalArgumentException("The result of the query was not an $entityClass")
                    }
                }

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