package net.corda.crypto.softhsm.impl

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.crypto.persistence.getEntityManagerFactory
import net.corda.crypto.softhsm.SigningRepositoryFactory
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.metrics.CordaMetrics
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService

@Suppress("LongParameterList")
class SigningRepositoryFactoryImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val keyEncodingService: KeyEncodingService,
    private val digestService: PlatformDigestService,
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : SigningRepositoryFactory {
    override fun getInstance(tenantId: String) =
        CordaMetrics.Metric.Crypto.SigningRepositoryGetInstanceTimer.builder()
            .withTag(CordaMetrics.Tag.Tenant, tenantId)
            .build()
            .recordCallable {
                SigningRepositoryImpl(
                    entityManagerFactory = getEntityManagerFactory(
                        tenantId,
                        dbConnectionManager,
                        virtualNodeInfoReadService,
                        jpaEntitiesRegistry
                    ),
                    tenantId = tenantId,
                    keyEncodingService = keyEncodingService,
                    digestService = digestService,
                    layeredPropertyMapFactory = layeredPropertyMapFactory
                )
            }!!
}