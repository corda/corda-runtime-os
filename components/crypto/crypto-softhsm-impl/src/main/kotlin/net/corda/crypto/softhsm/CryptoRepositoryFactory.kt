package net.corda.crypto.softhsm

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService

fun interface CryptoRepositoryFactory {
    /**
     * Get access to crypto repository a specific tenant
     *
     * @param tenantId the ID to use (e.g. a virtual node holding ID, P2P or REST
     * @param dbConnectionManager used to make the database connection
     * @param jpaEntitiesRegistry
     * @param virtualNodeInfoReadService used to get the connection information for a virtual node
     * @return an object for using the database
     */
    fun create(
        tenantId: String,
        dbConnectionManager: DbConnectionManager,
        jpaEntitiesRegistry: JpaEntitiesRegistry,
        virtualNodeInfoReadService: VirtualNodeInfoReadService,
    ): CryptoRepository
}