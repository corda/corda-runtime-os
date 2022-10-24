package net.corda.membership.certificate.service.impl

import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.CertificateUsage
import net.corda.membership.certificates.CertificateUsage.Companion.publicName
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
            useCertificateProcessor(CertificateUsage.fromAvro(request)) { processor ->
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
                        throw CertificatesServiceException("Unknown request: $request")
                    }
                }
                respFuture.complete(CertificateRpcResponse(payload))
            }
        } catch (e: Throwable) {
            respFuture.completeExceptionally(e)
        }
    }

    internal interface CertificateProcessor {
        fun saveCertificates(alias: String, certificates: String)
        fun readCertificates(alias: String): String?
        fun readAllCertificates(): List<String>
    }

    inner class ClusterCertificateProcessor(
        type: CertificateUsage.Type
    ) : CertificateProcessor {
        private val factory = dbConnectionManager.getClusterEntityManagerFactory()
        private val typeName = type.asAvro.publicName

        override fun saveCertificates(alias: String, certificates: String) {
            factory.transaction { em ->
                em.merge(ClusterCertificate(typeName, alias, certificates))
            }
        }

        override fun readCertificates(alias: String) = factory.transaction { em ->
            em.find(ClusterCertificate::class.java, ClusterCertificatePrimaryKey(typeName, alias))?.rawCertificate
        }

        override fun readAllCertificates() = factory.transaction { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(ClusterCertificate::class.java)
            val root = queryBuilder.from(ClusterCertificate::class.java)
            val query = queryBuilder
                .select(root)
                .where(
                    criteriaBuilder.equal(root.get<String>("type"), typeName),
                ).orderBy(criteriaBuilder.asc(root.get<String>("alias")))
            em.createQuery(query)
                .resultList
                .map { it.rawCertificate }
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

        override fun readAllCertificates() = factory.transaction { em ->
            em.createNamedQuery("Certificate.findAll", Certificate::class.java)
                .resultList
                .map { it.rawCertificate }
        }
    }

    internal fun <T> useCertificateProcessor(
        usage: CertificateUsage?,
        block: (CertificateProcessor) -> T
    ) {
        when (usage) {
            is CertificateUsage.Type -> {
                val processor = ClusterCertificateProcessor(usage)
                block.invoke(processor)
            }
            is CertificateUsage.HoldingIdentityId -> {
                useCertificateProcessor(usage.holdingIdentityId, block)
            }
            else -> {
                throw UnknownTypeException(usage)
            }
        }
    }
    private fun <T> useCertificateProcessor(holdingIdentityId: ShortHash, block: (CertificateProcessor) -> T) {
        val node = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityId)
            ?: throw NoSuchNode(holdingIdentityId)
        val factory = dbConnectionManager.createEntityManagerFactory(
            connectionId = node.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw IllegalStateException(
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
