package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class DbConnectionOpsCachedImpl(
    private val delegate: DbConnectionOps,
    private val entitiesRegistry: JpaEntitiesRegistry
    ): DbConnectionOps by delegate {

    // TODO - replace with caffeine cache
    private val cache = ConcurrentHashMap<Pair<String,DbPrivilege>, EntityManagerFactory>()

    private fun removeFromCache(name: String, privilege: DbPrivilege) {
        val entityManagerFactory = cache.remove(Pair(name,privilege))
        entityManagerFactory?.close()
    }

    override fun putConnection(name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID {
        return delegate.putConnection(name, privilege, config, description, updateActor)
            .apply { removeFromCache(name, privilege) }
    }

    override fun putConnection(entityManager: EntityManager, name: String, privilege: DbPrivilege, config: SmartConfig,
                               description: String?, updateActor: String): UUID {
        return delegate.putConnection(entityManager, name, privilege, config, description, updateActor)
            .apply { removeFromCache(name, privilege) }
    }

    override fun getOrCreateEntityManagerFactory(db: CordaDb, privilege: DbPrivilege): EntityManagerFactory {
        val entitiesSet =
            entitiesRegistry.get(db.persistenceUnitName) ?:
            throw DBConfigurationException("Entity set for ${db.persistenceUnitName} not found")

        return getOrCreateEntityManagerFactory(
            db.persistenceUnitName,
            privilege,
            entitiesSet)
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        return cache.computeIfAbsent(Pair(name,privilege)) {
            delegate.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)
        }
    }
}