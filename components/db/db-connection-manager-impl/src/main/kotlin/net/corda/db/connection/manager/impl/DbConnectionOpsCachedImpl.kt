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

    // We should try merging the below two caches into one to, like so, make sure each connection gets one EMF only,
    //  otherwise (i.e. if we get duplicate EMFs for same connection) we end up leaking memory with
    //  duplicate entity proxies loaded in the class loader as identified in CORE-15806.
    private val cache = ConcurrentHashMap<Pair<String,DbPrivilege>, EntityManagerFactory>()

    private val cacheByConnectionId = ConcurrentHashMap<Pair<UUID,Boolean>, Pair<EntityManagerFactory,Int>>()

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

        val emf = getOrCreateEntityManagerFactory(
            db.persistenceUnitName,
            privilege,
            entitiesSet)
        return object : EntityManagerFactory by emf {
            override fun close() {
                // consumers of this function are not responsible for closing the EMF. Calling close becomes a no-op.
            }
        }
    }

    override fun getOrCreateEntityManagerFactory(
        name: String,
        privilege: DbPrivilege,
        entitiesSet: JpaEntitiesSet
    ): EntityManagerFactory {
        val emf = cache.computeIfAbsent(Pair(name,privilege)) {
            delegate.getOrCreateEntityManagerFactory(name, privilege, entitiesSet)
        }
        return object : EntityManagerFactory by emf {
            override fun close() {
                // consumers of this function are not responsible for closing the EMF. Calling close becomes a no-op.
            }
        }
    }

    override fun getOrCreateEntityManagerFactory(
        connectionId: UUID,
        entitiesSet: JpaEntitiesSet,
        enablePool: Boolean,
    ): EntityManagerFactory {
        val entities = entitiesSet.classes.hashCode()
        val emfP = cacheByConnectionId.computeIfAbsent(Pair(connectionId, enablePool)) {
            Pair(delegate.createEntityManagerFactory(connectionId, entitiesSet, enablePool), entities)
        }
        if(entities != emfP.second)
            throw IllegalArgumentException("EntityManagerFactory with a different JpaEntitiesSet already exists.")
        return object : EntityManagerFactory by emfP.first {
            override fun close() {
                // consumers of this function are not responsible for closing the EMF. Calling close becomes a no-op.
            }
        }
    }
}