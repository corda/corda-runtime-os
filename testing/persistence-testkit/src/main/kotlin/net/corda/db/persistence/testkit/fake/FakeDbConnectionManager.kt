package net.corda.db.persistence.testkit.fake

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DataSourceFactory
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.db.testkit.DbUtils
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.DbEntityManagerConfiguration
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource


// TODO - move into re-usable fake.
//@ServiceRanking(Int.MAX_VALUE)
//@Component(service = [DbConnectionManager::class, FakeDbConnectionManager::class])
@Suppress("TooManyFunctions")
class FakeDbConnectionManager(
    connections: List<Pair<UUID, String>>,
    private val schemaName: String,
    private val emff: EntityManagerFactoryFactory = EntityManagerFactoryFactoryImpl()
): DbConnectionManager, DbConnectionOps, DataSourceFactory {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private data class NamedDataSources(val id: UUID, val name: String, val dataSource: CloseableDataSource)

    private val dbSources: List<NamedDataSources> = connections.map {
        val source = DbUtils.getEntityManagerConfiguration(
            "fake-db-manager-db-$schemaName",
            schemaName = "$schemaName${it.second.replace("-","")}",
            createSchema = true).dataSource
        NamedDataSources(it.first, it.second, source)
    }

    override fun createEntityManagerFactory(connectionId: UUID, entitiesSet: JpaEntitiesSet):
            EntityManagerFactory {
        val source = dbSources.single { it.id == connectionId }
        return emff.create(
            source.name,
            entitiesSet.classes.toList(),
            DbEntityManagerConfiguration(source.dataSource),
        )
    }

    fun getDataSource(id: UUID): CloseableDataSource {
        return dbSources.single { it.id ==  id}.dataSource
    }

    private var smartConfig: SmartConfig? = null

    override fun initialise(config: SmartConfig) {
        smartConfig = config
        logger.info("Fake DbConnectionManager initialised with $config")
    }

    override val clusterConfig: SmartConfig
        get() = smartConfig!!

    override fun bootstrap(config: SmartConfig) {
        smartConfig = config
        logger.info("Fake DbConnectionManager bootstrapped with $config")
    }

    override fun testConnection(): Boolean {
        return true
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
        logger.info("Fake DbConnectionManager started")
    }

    override fun stop() {
        dbSources.forEach { it.dataSource.close() }
        logger.info("Fake DbConnectionManager stopped")
    }

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        TODO("Not yet implemented")
    }

    override fun putConnection(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        TODO("Not yet implemented")
    }

    override fun getClusterDataSource(): DataSource {
        TODO("Not yet implemented")
    }

    override fun createDatasource(connectionId: UUID): CloseableDataSource {
        TODO("Not yet implemented")
    }

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? {
        TODO("Not yet implemented")
    }

    override fun getDataSource(config: SmartConfig): CloseableDataSource {
        TODO("Not yet implemented")
    }

    override fun getClusterEntityManagerFactory(): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        TODO("Not yet implemented")
    }

    override fun create(
        driverClass: String,
        jdbcUrl: String,
        username: String,
        password: String,
        isAutoCommit: Boolean,
        maximumPoolSize: Int
    ): CloseableDataSource {
        TODO("Not yet implemented")
    }

}
