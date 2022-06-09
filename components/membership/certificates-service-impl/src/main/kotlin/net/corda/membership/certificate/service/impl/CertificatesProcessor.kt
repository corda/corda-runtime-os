package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.CryptoTenants
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.messaging.api.processor.RPCResponderProcessor
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
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
            val processor = getCertificateProcessor(request.tenantId)
            val payload = when (val requestPayload = request.request) {
                is ImportCertificateRpcRequest -> {
                    processor.saveCertificate(requestPayload.alias, requestPayload.certificate)
                    CertificateImportedRpcResponse()
                }
                else -> {
                    throw CertificatesServiceException("Unknwon request: $request")
                }
            }
            respFuture.complete(CertificateRpcResponse(payload))
        } catch (e: Throwable) {
            respFuture.completeExceptionally(e)
        }
    }

    private interface CertificateProcessor {
        fun saveCertificate(alias: String, certificate: String)
    }

    inner class ClusterCertificateProcessor(
        private val tenantId: String
    ) : CertificateProcessor {
        private val factory = dbConnectionManager.getClusterEntityManagerFactory()

        override fun saveCertificate(alias: String, certificate: String) {
            factory.transaction { em ->
                em.merge(ClusterCertificate(tenantId, alias, certificate))
            }
        }
    }

    inner class NodeCertificateProcessor(
        private val factory: EntityManagerFactory
    ) : CertificateProcessor {
        override fun saveCertificate(alias: String, certificate: String) {
            factory.transaction { em ->
                em.merge(Certificate(alias, certificate))
            }
        }
    }

    private fun getCertificateProcessor(tenantId: String): CertificateProcessor {
        return if ((tenantId == CryptoTenants.P2P) || (tenantId == CryptoTenants.RPC_API)) {
            ClusterCertificateProcessor(tenantId)
        } else {
            val info = virtualNodeInfoReadService.getById(tenantId) ?: throw NoSuchNode(tenantId)
            val factory = dbConnectionManager.createEntityManagerFactory(
                info.vaultDmlConnectionId,
                jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                    ?: throw java.lang.IllegalStateException(
                        "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                    )
            )
            NodeCertificateProcessor((factory))
        }
    }
}
