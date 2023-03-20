package net.corda.crypto.softhsm

import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.crypto.softhsm.impl.V1CryptoRepositoryImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import javax.persistence.EntityManagerFactory

/**
 * Get access to crypto repository a specific tenant
 *
 * @param tenantId the ID to use (e.g. a virtual node holding ID, P2P or REST
 * @param dbConnectionManager used to make the database connection
 * @param jpaEntitiesRegistry
 * @param virtualNodeInfoReadService used to get the connection information for a virtual node
 * @return an object for using the database
 */

// Since this function requires no state and there is no need to access it outside this
// package it can be a simple static function.  It would be possible to make this an
// OSGi component with an interface. Since DbConnectionManager and
// VirtualNodeInfoReadService are lifecycle, this would have to be lifecycle as well.
// That adds up to quite a bit of extra code, and makes this hard to test.
//
// A difficulty with testing this is that if it is called directly it is hard
// to override. That can be resolved by the calling code being taking a function
// reference as an argument (i.e. being higher order).

fun cryptoRepositoryFactory(
    tenantId: String,
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
): CryptoRepository {
    val onCluster = CryptoTenants.isClusterTenant(tenantId)
    val entityManagerFactory = if (onCluster) {
        // tenantID is crypto, P2P or REST; let's obtain a connection to our cluster Crypto database
        val baseEMF = dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.Crypto, DbPrivilege.DML)
        object : EntityManagerFactory by baseEMF {
            override fun close() {
                // ignored; we should never close this since dbConnectionManager owns it
                // TODO maybe move this logic to never close to DbConnectionManager
            }
        }
    } else {
        // tenantID is a virtual node; let's connect to one of the virtual node Crypto databases
        dbConnectionManager.createEntityManagerFactory(
            connectionId = virtualNodeInfoReadService.getByHoldingIdentityShortHash(
                ShortHash.of(
                    tenantId
                )
            )?.cryptoDmlConnectionId
                ?: throw IllegalStateException(
                    "virtual node for $tenantId is not registered."
                ),
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Crypto.persistenceUnitName)
                ?: throw IllegalStateException(
                    "persistenceUnitName ${CordaDb.Crypto.persistenceUnitName} is not registered."
                )
        )
    }

//        // somehow figure out which version we want, e.g.
//        when (entityManagerFactory.getSchemaVersion()) {
//            1 -> V1CryptoRepositoryImpl(entityManagerFactory)
//            else -> V2CryptoRepositoryImpl(entityManagerFactory)
//        }

    return V1CryptoRepositoryImpl(entityManagerFactory)
}
