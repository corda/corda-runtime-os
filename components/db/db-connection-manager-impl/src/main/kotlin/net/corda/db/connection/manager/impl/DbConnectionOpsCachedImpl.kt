package net.corda.db.connection.manager.impl

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionOps
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class DbConnectionOpsCachedImpl(
    private val delegate: DbConnectionOps,
    private val entitiesRegistry: JpaEntitiesRegistry
    ): DbConnectionOps by delegate {

    private val cache: Cache<Pair<String,DbPrivilege>, EntityManagerFactory> =
        CacheFactoryImpl().build(
            "",
            Caffeine.newBuilder()
                .maximumSize(30)
                .removalListener { _ , value, _ ->
                    value?.close()
                }
        )

    private fun removeFromCache(name: String, privilege: DbPrivilege) {
        cache.invalidate(Pair(name,privilege))
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
        return cache.get(Pair(name,privilege)) {
            delegate.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)
        }
    }
}