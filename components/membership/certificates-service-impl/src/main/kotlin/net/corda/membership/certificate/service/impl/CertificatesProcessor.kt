package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.CryptoTenants
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.membership.certificates.datamodel.ClusterCertificatePrimaryKey
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManagerFactory

internal class CertificatesProcessor(
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) :
    RPCResponderProcessor<CertificateRpcRequest, CertificateRpcResponse> {

    override fun onNext(request: CertificateRpcRequest, respFuture: CompletableFuture<CertificateRpcResponse>) {
        try {
            useCertificateProcessor(request.tenantId) { processor->
                val payload = when (val requestPayload = request.request) {
                    is ImportCertificateRpcRequest -> {
                        processor.saveCertificates(requestPayload.alias, requestPayload.certificates)
                        CertificateImportedRpcResponse()
                    }
                    is RetrieveCertificateRpcRequest -> {
                        val certificates = processor.readCertificates(requestPayload.alias)
                        CertificateRetrievalRpcResponse(certificates)
                    }
                    else -> {
                        throw CertificatesServiceException("Unknwon request: $request")
                    }
                }
                respFuture.complete(CertificateRpcResponse(payload))
            }
        } catch (e: Throwable) {
            respFuture.completeExceptionally(e)
        }
    }

    private interface CertificateProcessor {
        fun saveCertificates(alias: String, certificates: String)
        fun readCertificates(alias: String): String?
    }

    inner class ClusterCertificateProcessor(
        private val tenantId: String
    ) : CertificateProcessor {
        private val factory = dbConnectionManager.getClusterEntityManagerFactory()

        override fun saveCertificates(alias: String, certificates: String) {
            factory.transaction { em ->
                em.merge(ClusterCertificate(tenantId, alias, certificates))
            }
        }

        override fun readCertificates(alias: String) = factory.transaction { em ->
            em.find(ClusterCertificate::class.java, ClusterCertificatePrimaryKey(tenantId, alias))?.rawCertificate
        }
    }

    inner class NodeCertificateProcessor(
        private val factory: EntityManagerFactory
    ) : CertificateProcessor {
        override fun saveCertificates(alias: String, certificates: String) {
            factory.transaction { em ->
                em.merge(Certificate(alias, certificates))
            }
        }

        override fun readCertificates(alias: String) = factory.transaction { em ->
            em.find(Certificate::class.java, alias)?.rawCertificate
        }
    }

    private fun <T> useCertificateProcessor(tenantId: String, block: (CertificateProcessor)->T) {
        if ((tenantId == CryptoTenants.P2P) || (tenantId == CryptoTenants.RPC_API)) {
            val processor = ClusterCertificateProcessor(tenantId)
            block.invoke(processor)
        } else {
            val node = virtualNodeInfoReadService.getByHoldingIdentityShortHash(ShortHash.of(tenantId))
                ?: throw NoSuchNode(tenantId)
            val factory = dbConnectionManager.createEntityManagerFactory(
                connectionId = node.vaultDmlConnectionId,
                entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                    ?: throw java.lang.IllegalStateException(
                        "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                    )
            )
            try {
                val processor = NodeCertificateProcessor(factory)
                block.invoke(processor)
            } finally {
                factory.close()
            }
        }
    }
}
