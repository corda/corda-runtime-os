package net.corda.crypto.persistence

import javax.persistence.EntityManagerFactory
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.metrics.CordaMetrics
import net.corda.orm.JpaEntitiesRegistry
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("QQQ")
private val openConnections = ConcurrentHashMap<String, Long>()
fun getEntityManagerFactory(
    tenantId: String,
    dbConnectionManager: DbConnectionManager,
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    jpaEntitiesRegistry: JpaEntitiesRegistry,
): EntityManagerFactory {
    return CordaMetrics.Metric.Crypto.EntityManagerFactoryCreationTimer.builder()
        .withTag(CordaMetrics.Tag.Tenant, tenantId)
        .build()
        .recordCallable {
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
                val created = Exception("YYY")
                logger.info("creating Manager for $tenantId, threadId: ${Thread.currentThread().id}", created)
                openConnections.compute(tenantId) { k, v ->
                    if (v == null) {
                        0
                    } else {
                        logger.info("Connections for $k was created while another one is alive", Exception("TTT"))
                        v + 1
                    }
                }
                val manager = dbConnectionManager.createEntityManagerFactory(
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
                object : EntityManagerFactory by manager {
                    override fun close() {
                        logger.info("Closing Manager for $tenantId, threadId: ${Thread.currentThread().id}", Exception("PPP", created))
                        openConnections.compute(tenantId) { _, v ->
                            if ((v == null) || (v <= 1)) {
                                null
                            } else {
                                v - 1
                            }
                        }
                        manager.close()
                    }
                }
            }
            entityManagerFactory
        }!!
}
