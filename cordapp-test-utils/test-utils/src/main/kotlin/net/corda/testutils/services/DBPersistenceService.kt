package net.corda.testutils.services

import net.corda.testutils.internal.H2PersistenceUnitInfo
import net.corda.testutils.internal.cast
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
import javax.persistence.EntityManagerFactory

class DBPersistenceService(
    x500 : MemberX500Name,
    val emf: EntityManagerFactory = createH2EntityManagerFactory(x500)) : PersistenceService{

    companion object { }

    override fun <T : Any> find(entityClass: Class<T>, primaryKey: Any): T? {
        TODO("Not yet implemented")
    }

    override fun <T : Any> find(entityClass: Class<T>, primaryKeys: List<Any>): List<T> {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun <T : Any> merge(entities: List<T>): List<T> {
        TODO("Not yet implemented")
    }

    override fun persist(entity: Any) {
        val em = emf.createEntityManager()
        val transaction = em.transaction
        transaction.begin()
        try {
            em.persist(entity)
            transaction.commit()
        } catch (e: RuntimeException) {
            if (transaction.isActive) { transaction.rollback() }
            throw e
        }
    }

    override fun persist(entities: List<Any>) {
        TODO("Not yet implemented")
    }

    override fun <T : Any> query(queryName: String, entityClass: Class<T>): ParameterisedQuery<T> {
        TODO("Not yet implemented")
    }

    override fun remove(entity: Any) {
        TODO("Not yet implemented")
    }

    override fun remove(entities: List<Any>) {
        TODO("Not yet implemented")
    }

}


fun createH2EntityManagerFactory(x500 : MemberX500Name): EntityManagerFactory {
    return HibernatePersistenceProvider()
        .createContainerEntityManagerFactory(
            H2PersistenceUnitInfo(),
            mapOf(
                JPA_JDBC_DRIVER to "org.hsqldb.jdbcDriver",
                JPA_JDBC_URL to "jdbc:hsqldb:mem:${x500.shortName}",
                JPA_JDBC_USER to "admin",
                JPA_JDBC_PASSWORD to "",
                DIALECT to HSQLDialect::class.java.name,
                HBM2DDL_AUTO to "create",
            )
        )
}

private val MemberX500Name.shortName: String
    get() { return this.commonName ?: this.organisation }