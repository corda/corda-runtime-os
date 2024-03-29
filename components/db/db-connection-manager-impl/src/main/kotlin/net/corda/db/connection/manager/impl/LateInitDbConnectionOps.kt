package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.CloseableDataSource
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesSet
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.sql.DataSource

/**
 * [DbConnectionOps] delegate that supports late initialization
 */
class LateInitDbConnectionOps: DbConnectionOps {
    lateinit var delegate: DbConnectionOps

    override fun putConnection(
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        return delegate.putConnection(name, privilege, config, description, updateActor)
    }

    override fun putConnection(
        entityManager: EntityManager,
        name: String,
        privilege: DbPrivilege,
        config: SmartConfig,
        description: String?,
        updateActor: String
    ): UUID {
        return delegate.putConnection(entityManager, name, privilege, config, description, updateActor)
    }

    override fun getClusterDataSource(): DataSource = delegate.getClusterDataSource()

    override fun createDatasource(connectionId: UUID, enablePool: Boolean): CloseableDataSource =
        delegate.createDatasource(connectionId, enablePool)

    override fun getDataSource(name: String, privilege: DbPrivilege): DataSource? =
        delegate.getDataSource(name, privilege)

    override fun getDataSource(config: SmartConfig, enablePool: Boolean): CloseableDataSource =
        delegate.getDataSource(config, enablePool)

    override fun getClusterEntityManagerFactory(): EntityManagerFactory =
        delegate.getClusterEntityManagerFactory()

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory =
        delegate.getOrCreateEntityManagerFactory(db, privilege)

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory =
        delegate.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)

    override fun createEntityManagerFactory(
        connectionId: UUID,
        entitiesSet: JpaEntitiesSet,
        enablePool: Boolean,
        ):
            EntityManagerFactory = delegate.createEntityManagerFactory(connectionId, entitiesSet, enablePool)

    override fun getOrCreateEntityManagerFactory(
        connectionId: UUID,
        entitiesSet: JpaEntitiesSet,
        enablePool: Boolean,
    ): EntityManagerFactory =
        delegate.getOrCreateEntityManagerFactory(connectionId, entitiesSet, enablePool)
}