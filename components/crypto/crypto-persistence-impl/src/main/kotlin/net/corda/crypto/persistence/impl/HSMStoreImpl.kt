package net.corda.crypto.persistence.impl

import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.persistence.hsm.HSMUsage
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.util.toHex
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.UUID
import javax.persistence.Tuple

@Component(service = [HSMStore::class])
class HSMStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoConnectionsFactory::class)
    private val connectionsFactory: CryptoConnectionsFactory,
    @Reference(service = CipherSchemeMetadata::class)
    private val schemaMetadata: CipherSchemeMetadata
) : AbstractComponent<HSMStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMStore>(),
    upstream = DependenciesTracker.Default(
        setOf(LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>())
    )
), HSMStore {

    override fun createActiveImpl(): Impl = Impl(connectionsFactory, schemaMetadata)

    override fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation? =
        impl.findTenantAssociation(tenantId, category)

    override fun getHSMUsage(): List<HSMUsage> = impl.getHSMUsage()

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMTenantAssociation = impl.associate(tenantId, category, hsmId, masterKeyPolicy)

    class Impl(
        private val connectionsFactory: CryptoConnectionsFactory,
        schemaMetadata: CipherSchemeMetadata
    ) : AbstractImpl {
        private val secureRandom = schemaMetadata.secureRandom

        fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation? =
            entityManagerFactory().use {
                val result = it.createQuery(
                    """
            SELECT ca FROM HSMCategoryAssociationEntity ca
                JOIN FETCH ca.hsmAssociation a
            WHERE ca.category = :category 
                AND ca.tenantId = :tenantId
                AND ca.deprecatedAt = 0
                AND a.tenantId = :tenantId
            """.trimIndent(),
                    HSMCategoryAssociationEntity::class.java
                ).setParameter("category", category).setParameter("tenantId", tenantId).resultList
                if (result.isEmpty()) {
                    null
                } else {
                    result[0].toHSMTenantAssociation()
                }
            }

        fun getHSMUsage(): List<HSMUsage> = entityManagerFactory().use {
            it.createQuery(
                """
            SELECT ha.hsmId as hsmId, COUNT(*) as usages 
            FROM HSMCategoryAssociationEntity ca JOIN ca.hsmAssociation ha
            GROUP by ha.hsmId
            """.trimIndent(),
                Tuple::class.java
            ).resultList.map { record ->
                HSMUsage(
                    usages = (record.get("usages") as Number).toInt(),
                    hsmId = record.get("hsmId") as String
                )
            }
        }

        fun associate(
            tenantId: String,
            category: String,
            hsmId: String,
            masterKeyPolicy: MasterKeyPolicy
        ): HSMTenantAssociation {
            val association = findHSMAssociationEntity(tenantId, hsmId)
                ?: createAndPersistAssociation(tenantId, hsmId, masterKeyPolicy)
            val categoryAssociation = HSMCategoryAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                category = category,
                timestamp = Instant.now(),
                hsmAssociation = association,
                deprecatedAt = 0
            )
            entityManagerFactory().transaction {
                it.persist(categoryAssociation)
            }
            return categoryAssociation.toHSMTenantAssociation()
        }

        private fun findHSMAssociationEntity(
            tenantId: String,
            hsmId: String
        ) = entityManagerFactory().use {
            it.createQuery(
            """
            SELECT a 
            FROM HSMAssociationEntity a
            WHERE a.tenantId = :tenantId AND a.hsmId = :hsmId
            """.trimIndent(),
                HSMAssociationEntity::class.java
            ).setParameter("tenantId", tenantId).setParameter("hsmId", hsmId).resultList.singleOrNull()
        }

        private fun createAndPersistAssociation(
            tenantId: String,
            hsmId: String,
            masterKeyPolicy: MasterKeyPolicy
        ): HSMAssociationEntity {
            val aliasSecret = ByteArray(32)
            secureRandom.nextBytes(aliasSecret)
            val association = HSMAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                hsmId = hsmId,
                timestamp = Instant.now(),
                masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                    generateRandomShortAlias()
                } else {
                    null
                },
                aliasSecret = aliasSecret
            )
            entityManagerFactory().transaction {
                it.persist(association)
            }
            return association
        }

        private fun generateRandomShortAlias() =
            UUID.randomUUID().toString().toByteArray().toHex().take(12)

        private fun HSMCategoryAssociationEntity.toHSMTenantAssociation() = HSMTenantAssociation(
            id = id,
            tenantId = hsmAssociation.tenantId,
            category = category,
            masterKeyAlias = hsmAssociation.masterKeyAlias,
            aliasSecret = hsmAssociation.aliasSecret,
            hsmId = hsmAssociation.hsmId,
            deprecatedAt = deprecatedAt
        )

        private fun entityManagerFactory() = connectionsFactory.getEntityManagerFactory(CryptoTenants.CRYPTO)
    }
}