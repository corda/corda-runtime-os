package net.corda.simulator.runtime.persistence

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.simulator.runtime.utils.sandboxName
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.application.persistence.PagedQuery
import net.corda.v5.application.persistence.ParameterizedQuery
import net.corda.v5.base.types.MemberX500Name
import org.hibernate.Session
import org.hibernate.cfg.AvailableSettings.DIALECT
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_DRIVER
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_PASSWORD
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_URL
import org.hibernate.cfg.AvailableSettings.JPA_JDBC_USER
import org.hibernate.dialect.HSQLDialect
import org.hibernate.jpa.HibernatePersistenceProvider
import org.slf4j.LoggerFactory
import java.sql.Connection
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

/**
 * An implementation of PersistenceService that uses JPA, Hibernate and HSQLDB.
 *
 * @param member The member for whom the PersistenceService is being created.
 *
 * @see [net.corda.v5.application.persistence.PersistenceService] for details of methods.
 */
class DbPersistenceService(member : MemberX500Name) : CloseablePersistenceService {

    private val emf : EntityManagerFactory = createEntityManagerFactory(member)

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        fun createEntityManagerFactory(member: MemberX500Name): EntityManagerFactory {
            log.info("Creating EntityManagerFactory")
            val emf = HibernatePersistenceProvider()
                .createContainerEntityManagerFactory(
                    JpaPersistenceUnitInfo(),
                    mapOf(
                        JPA_JDBC_DRIVER to "org.hsqldb.jdbcDriver",
                        JPA_JDBC_URL to "jdbc:hsqldb:mem:${member.sandboxName};shutdown=true",
                        JPA_JDBC_USER to "admin",
                        JPA_JDBC_PASSWORD to "",
                        DIALECT to HSQLDialect::class.java.name,
                        "hibernate.show_sql" to "true",
                        "hibernate.format_sql" to "true",
                    )
                )

            // NOTE: only need the connection here, creating EM is a bit wasteful but ok for now.
            //  alternative option would be to create a connection manually here.
            try {
                emf.createEntityManager().use { em ->
                    em.unwrap(Session::class.java).doWork {

                        log.info("Running migrations for \"$member\"")
                        runMigrations(it)
                        log.info("Migrations for \"$member\" done")
                    }
                }
            } catch (e: Exception) {
                log.error("Exception encountered in db migrations for \"$member\"")
                throw e
            }
            return emf
        }

        private fun runMigrations(connection: Connection) {
            val classloaderChangeLog = ClassloaderChangeLog(
                linkedSetOf(
                    ClassloaderChangeLog.ChangeLogResourceFiles(
                        "simulator-${DbPersistenceService::javaClass.name}",
                        @Suppress("ForbiddenComment")
                        // TODO: constant in `VirtualNodeDbChangeLog.MASTER_CHANGE_LOG'
                        //   should move into API repo.
                        listOf("migration/simulator.changelog-master.xml", "migration/db.changelog-master.xml")
                    ),
                )
            )

            val lbm = LiquibaseSchemaMigratorImpl()
            lbm.updateDb(connection, classloaderChangeLog)
        }

        private fun <R> EntityManagerFactory.transaction(block: (EntityManager) -> R): R {
            try {
                return this.createEntityManager().use {
                    inTransaction(it, block)
                }
            } catch (e: Exception) {
                throw CordaPersistenceException(e.message ?: "Error in persistence", e)
            }
        }

        private fun <R> inTransaction(it: EntityManager, block: (EntityManager) -> R): R {
            val t = it.transaction
            t.begin()
            return try {
                block(it)
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

        private fun <R> EntityManagerFactory.guard(block: (EntityManager) -> R): R {
            return this.createEntityManager().use {
                try {
                    block(it)
                } catch (e: Exception) {
                    throw CordaPersistenceException(e.message ?: "Error in persistence", e)
                }
            }
        }

        private fun <R> EntityManager.use(block: (EntityManager) -> R): R {
            return try {
                block(this)
            } finally {
                close()
            }
        }

        private data class QueryContext(val offset: Int, val limit: Int, val parameters: Map<String, Any>)

        private class ParameterizedQueryBase<T>(
            private val emf: EntityManagerFactory,
            private val queryName: String,
            private val entityClass: Class<T>,
            private val context: QueryContext = QueryContext(
                0,
                Int.MAX_VALUE,
                mapOf<String, Any>()
            )
        ) : ParameterizedQuery<T> {
            override fun execute(): List<T> {
                return emf.transaction { em ->
                    val query = em.createNamedQuery(queryName, entityClass)
                        .setFirstResult(context.offset)
                        .setMaxResults(context.limit)
                    context.parameters.entries.fold(query) { q, (key, value) -> q.setParameter(key, value) }.resultList
                }
            }

            override fun setLimit(limit: Int): ParameterizedQuery<T> {
                return ParameterizedQueryBase(emf, queryName, entityClass, context.copy(limit = limit))
            }

            override fun setOffset(offset: Int): ParameterizedQuery<T> {
                return ParameterizedQueryBase(emf, queryName, entityClass, context.copy(offset = offset))
            }

            override fun setParameter(name: String, value: Any): ParameterizedQuery<T> {
                return ParameterizedQueryBase(
                    emf,
                    queryName,
                    entityClass,
                    context.copy(parameters = context.parameters.plus(Pair(name, value)))
                )
            }

            override fun setParameters(parameters: Map<String, Any>): ParameterizedQuery<T> {
                return ParameterizedQueryBase(
                    emf,
                    queryName,
                    entityClass,
                    parameters.entries.fold(context) { c, (key, value) ->
                        c.copy(parameters = context.parameters.plus(Pair(key, value)))
                    }
                )
            }
        }

        private class PagedQueryBase<T>(
            private val emf: EntityManagerFactory,
            private val entityClass: Class<T>,
            private val context: QueryContext = QueryContext(0, Int.MAX_VALUE, mapOf())
        ) : PagedQuery<T> {
            override fun execute(): List<T> {
                try {
                    return emf.guard {
                        val query = it.createQuery("FROM ${entityClass.simpleName} e")
                            .setFirstResult(context.offset)
                            .setMaxResults(context.limit)
                        @Suppress("UNCHECKED_CAST")
                        query.resultList as List<T>
                    }
                } catch (e: ClassCastException) {
                    throw CordaPersistenceException("The result of the query was not an $entityClass", e)
                }
            }

            override fun setLimit(limit: Int): PagedQuery<T> {
                return PagedQueryBase(emf, entityClass, context.copy(limit = limit))
            }

            override fun setOffset(offset: Int): PagedQuery<T> {
                return PagedQueryBase(emf, entityClass, context.copy(offset = offset))
            }

        }
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T? {
        return emf.guard {
            it.find(entityClass, primaryKey)
        }
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T> {
        return emf.guard {
            primaryKeys.map { pk -> it.find(entityClass, pk) }
        }
    }

    override fun <T : Any> findAll(entityClass: Class<T>): PagedQuery<T> {
        return PagedQueryBase<T>(emf, entityClass)
    }

    override fun <T : Any> merge(entity: T): T? {
        return emf.transaction {
            it.merge(entity)
        }
    }

    override fun <T : Any> merge(entities: List<T>): List<T> {
        return emf.transaction { em ->
            entities.map { em.merge(it) }
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

    override fun <T : Any> query(queryName: String, entityClass: Class<T>): ParameterizedQuery<T> {
        return ParameterizedQueryBase<T>(emf, queryName, entityClass)
    }

    override fun remove(entity: Any) {
        emf.transaction {
            it.remove(
                if (it.contains(entity)) {
                    entity
                } else {
                    it.merge(entity)
                }
            )
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
