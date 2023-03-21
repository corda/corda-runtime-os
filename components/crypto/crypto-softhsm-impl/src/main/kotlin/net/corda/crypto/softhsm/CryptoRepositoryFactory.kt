package net.corda.crypto.softhsm

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.config.impl.signingService
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.softhsm.impl.V1CryptoRepositoryImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService

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

@Suppress("LongParameterList")
fun cryptoRepositoryFactory(
    tenantId: String,
    config: SmartConfig,
    dbConnectionManager: DbConnectionManager,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    keyEncodingService: KeyEncodingService,
    digestService: PlatformDigestService,
    layeredPropertyMapFactory: LayeredPropertyMapFactory,
): CryptoRepository {
    val entityManagerFactory =
        getEntityManagerFactory(tenantId, dbConnectionManager, virtualNodeInfoReadService, jpaEntitiesRegistry)

    val cache = V1CryptoRepositoryImpl.createCache(config.signingService())

//        // somehow figure out which version we want, e.g.
//        when (entityManagerFactory.getSchemaVersion()) {
//            1 -> V1CryptoRepositoryImpl(entityManagerFactory)
//            else -> V2CryptoRepositoryImpl(entityManagerFactory)
//        }

    return V1CryptoRepositoryImpl(
        entityManagerFactory,
        cache,
        keyEncodingService,
        digestService,
        layeredPropertyMapFactory
    )
}

