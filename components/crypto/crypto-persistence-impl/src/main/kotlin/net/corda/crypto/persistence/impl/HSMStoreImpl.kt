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
        workerSetId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMTenantAssociation = impl.associate(tenantId, category, workerSetId, masterKeyPolicy)

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
                JOIN FETCH ca.hsm a
                JOIN FETCH a.config c
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
            SELECT ha.workerSetId workerSetId, COUNT(*) usages 
            FROM HSMCategoryAssociationEntity ca JOIN ca.hsmAssociation ha
            GROUP by ha.workerSetId
            """.trimIndent(),
                Tuple::class.java
            ).resultList.map {
                HSMUsage(
                    usages = (it.get("usages") as Number).toInt(),
                    workerSetId = it.get("workerSetId") as String
                )
            }
        }

        fun associate(
            tenantId: String,
            category: String,
            workerSetId: String,
            masterKeyPolicy: MasterKeyPolicy
        ): HSMTenantAssociation {
            val association = findHSMAssociationEntity(tenantId, workerSetId)
                ?: createAndPersistAssociation(tenantId, workerSetId, masterKeyPolicy)
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
            configId: String
        ) = entityManagerFactory().use {
            it.createQuery(
            """
            SELECT a 
            FROM HSMAssociationEntity a JOIN a.config c
            WHERE a.tenantId = :tenantId AND c.id = :configId
            """.trimIndent(),
                HSMAssociationEntity::class.java
            ).setParameter("tenantId", tenantId).setParameter("configId", configId).resultList.singleOrNull()
        }

        private fun createAndPersistAssociation(
            tenantId: String,
            workerSetId: String,
            masterKeyPolicy: MasterKeyPolicy
        ): HSMAssociationEntity {
            val aliasSecret = ByteArray(32)
            secureRandom.nextBytes(aliasSecret)
            val association = HSMAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                workerSetId = workerSetId,
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
            workerSetId = hsmAssociation.workerSetId,
            deprecatedAt = deprecatedAt
        )

        private fun entityManagerFactory() = connectionsFactory.getEntityManagerFactory(CryptoTenants.CRYPTO)
    }
}