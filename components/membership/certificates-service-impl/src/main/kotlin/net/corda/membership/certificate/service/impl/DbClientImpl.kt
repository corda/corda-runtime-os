package net.corda.membership.certificate.service.impl

import net.corda.data.certificates.CertificateUsage
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.certificates.datamodel.AllowedCertificate
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.CertificateEntity
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.utils.transaction
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import javax.persistence.EntityManagerFactory

internal class DbClientImpl(
    private val dbConnectionManager: DbConnectionManager,
    private val jpaEntitiesRegistry: JpaEntitiesRegistry,
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : DbCertificateClient {
    internal abstract class CertificateProcessor<E : CertificateEntity>(
        usage: CertificateUsage,
        val factory: EntityManagerFactory,
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

        fun listAliases(): List<String> = factory.transaction { em ->
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
                .map { it.alias }
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
        fun allowCertificate(
            subject: String,
        ) : Unit = factory.transaction { em ->
            em.merge(AllowedCertificate(subject))
        }

        fun disallowCertificate(
            subject: String,
        )= factory.transaction { em ->
            val certificate = em.find(AllowedCertificate::class.java, subject)
            if (certificate != null) {
                em.remove(certificate)
            }
        }

        fun listAllowedCertificates(): Collection<String> = factory.transaction { em ->
            val criteriaBuilder = em.criteriaBuilder
            val queryBuilder = criteriaBuilder.createQuery(AllowedCertificate::class.java)
            val root = queryBuilder.from(AllowedCertificate::class.java)
            val query = queryBuilder
                .select(root)
                .orderBy(criteriaBuilder.asc(root.get<String>("subject")))
            em.createQuery(query)
                .resultList
                .map { it.subject }
        }
    }

    private fun <T> useCertificateProcessor(
        holdingIdentityId: ShortHash?,
        usage: CertificateUsage,
        block: (CertificateProcessor<*>) -> T
    ): T {
        return if (holdingIdentityId == null) {
            val processor = ClusterCertificateProcessor(usage)
            block.invoke(processor)
        } else {
            useVirtualNodeCertificateProcessor(holdingIdentityId, usage, block)
        }
    }
    private fun <T> useVirtualNodeCertificateProcessor(
        holdingIdentityId: ShortHash,
        usage: CertificateUsage,
        block: (NodeCertificateProcessor) -> T,
    ): T {
        val node = virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityId)
            ?: throw NoSuchNode(holdingIdentityId)
        val factory = dbConnectionManager.createEntityManagerFactory(
            connectionId = node.vaultDmlConnectionId,
            entitiesSet = jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)
                ?: throw IllegalStateException(
                    "persistenceUnitName ${CordaDb.Vault.persistenceUnitName} is not registered."
                )
        )
        return try {
            val processor = NodeCertificateProcessor(factory, usage)
            block.invoke(processor)
        } finally {
            factory.close()
        }
    }

    override fun importCertificates(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
        alias: String,
        certificates: String,
    ) {
        useCertificateProcessor(holdingIdentityId, usage) { processor ->
            processor.saveCertificates(alias, certificates)
        }
    }

    override fun getCertificateAliases(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
    ): Collection<String> {
        return useCertificateProcessor(holdingIdentityId, usage) { processor ->
            processor.listAliases()
        }
    }

    override fun retrieveCertificates(
        holdingIdentityId: ShortHash?,
        usage: CertificateUsage,
        alias: String
    ): String? {
        return useCertificateProcessor(holdingIdentityId, usage) { processor ->
            processor.readCertificates(alias)
        }
    }

    override fun allowCertificate(holdingIdentityId: ShortHash, subject: String) =
        useVirtualNodeCertificateProcessor(holdingIdentityId, CertificateUsage.P2P_CLIENT_TLS) {
            it.allowCertificate(subject)
        }

    override fun disallowCertificate(holdingIdentityId: ShortHash, subject: String) =
        useVirtualNodeCertificateProcessor(holdingIdentityId, CertificateUsage.P2P_CLIENT_TLS) {
            it.disallowCertificate(subject)
        }

    override fun listAllowedCertificates(holdingIdentityId: ShortHash): Collection<String> =
        useVirtualNodeCertificateProcessor(holdingIdentityId, CertificateUsage.P2P_CLIENT_TLS) {
            it.listAllowedCertificates()
        }

}
