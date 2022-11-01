package net.corda.membership.certificate.service.impl

import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.CertificateEntity
import net.corda.membership.certificates.datamodel.ClusterCertificate
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
            val holdingIdentity = if (request.holdingIdentity == null) {
                null
            } else {
                ShortHash.of(request.holdingIdentity)
            }
            useCertificateProcessor(holdingIdentity, request.usage) { processor ->
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

    internal abstract class CertificateProcessor<E : CertificateEntity>(
        usage: CertificateUsage,
        private val factory: EntityManagerFactory,
    ) {
        private val usageName = usage.publicName

        abstract val entityClass: Class<E>
        abstract fun createEntity(usage: String, alias: String, certificates: String): E

        fun saveCertificates(alias: String, certificates: String) {
            factory.transaction { em ->
                em.merge(createEntity(usageName, alias, certificates))
            }
        }
        fun readCertificates(alias: String): String? = factory.transaction { em ->
            val entity = em.find(entityClass, alias) ?: return@transaction null
            if (entity.usage != usageName) {
                // This certificate has the correct alias but the wrong usage
                return@transaction null
            }
            entity.rawCertificate
        }

        fun readAllCertificates(): List<String> = factory.transaction { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(entityClass)
            val root = queryBuilder.from(entityClass)
            val query = queryBuilder
                .select(root)
                .where(
                    criteriaBuilder.equal(root.get<String>("usage"), usageName),
                ).orderBy(criteriaBuilder.asc(root.get<String>("alias")))
            em.createQuery(query)
                .resultList
                .map { it.rawCertificate }
        }
    }

    inner class ClusterCertificateProcessor(
        usage: CertificateUsage,
    ) : CertificateProcessor<ClusterCertificate>(
        usage,
        dbConnectionManager.getClusterEntityManagerFactory(),
    ) {

        override val entityClass = ClusterCertificate::class.java

        override fun createEntity(usage: String, alias: String, certificates: String) = ClusterCertificate(alias, usage, certificates)
    }

    inner class NodeCertificateProcessor(
        factory: EntityManagerFactory,
        usage: CertificateUsage,
    ) : CertificateProcessor<Certificate>(usage, factory) {
        override val entityClass = Certificate::class.java

        override fun createEntity(usage: String, alias: String, certificates: String) = Certificate(alias, usage, certificates)
    }

    internal fun <T> useCertificateProcessor(
        holdingIdentityId: ShortHash?,
        usage: CertificateUsage,
        block: (CertificateProcessor<*>) -> T
    ) {
        if (holdingIdentityId == null) {
            val processor = ClusterCertificateProcessor(usage)
            block.invoke(processor)
        } else {
            useCertificateProcessor(holdingIdentityId, usage, block)
        }
    }
    private fun <T> useCertificateProcessor(holdingIdentityId: ShortHash, usage: CertificateUsage, block: (CertificateProcessor<*>) -> T) {
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
            val processor = NodeCertificateProcessor(factory, usage)
            block.invoke(processor)
        } finally {
            factory.close()
        }
    }
}
