package net.corda.crypto.persistence.impl

import net.corda.crypto.component.impl.AbstractComponent
import net.corda.crypto.component.impl.DependenciesTracker
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.HSMStore
import net.corda.crypto.persistence.HSMUsage
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.v5.base.util.EncodingUtils.toHex
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.Tuple

@Component(service = [HSMStore::class])
class HSMStoreImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = CryptoConnectionsFactory::class)
    private val connectionsFactory: CryptoConnectionsFactory
) : AbstractComponent<HSMStoreImpl.Impl>(
    coordinatorFactory = coordinatorFactory,
    myName = LifecycleCoordinatorName.forComponent<HSMStore>(),
    upstream = DependenciesTracker.Default(
        setOf(LifecycleCoordinatorName.forComponent<CryptoConnectionsFactory>())
    )
), HSMStore {

    override fun createActiveImpl(): Impl = Impl(connectionsFactory)

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
        impl.findTenantAssociation(tenantId, category)

    override fun getHSMUsage(): List<HSMUsage> = impl.getHSMUsage()

    override fun associate(
        tenantId: String,
        category: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationInfo = impl.associate(tenantId, category, hsmId, masterKeyPolicy)

    class Impl(
        private val connectionsFactory: CryptoConnectionsFactory
    ) : AbstractImpl {
        fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
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
                    result[0].toHSMAssociation()
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
        ): HSMAssociationInfo = entityManagerFactory().use {
            it.transaction { em ->
                val association =
                    findHSMAssociationEntity(em, tenantId, hsmId)
                        ?: createAndPersistAssociation(em, tenantId, hsmId, masterKeyPolicy)

                val categoryAssociation = HSMCategoryAssociationEntity(
                    id = UUID.randomUUID().toString(),
                    tenantId = tenantId,
                    category = category,
                    timestamp = Instant.now(),
                    hsmAssociation = association,
                    deprecatedAt = 0
                )

                em.persist(categoryAssociation)
                categoryAssociation.toHSMAssociation()
            }
        }

        private fun findHSMAssociationEntity(
            entityManager: EntityManager,
            tenantId: String,
            hsmId: String
        ) =
            entityManager.createQuery(
                """
            SELECT a 
            FROM HSMAssociationEntity a
            WHERE a.tenantId = :tenantId AND a.hsmId = :hsmId
            """.trimIndent(),
                HSMAssociationEntity::class.java
            ).setParameter("tenantId", tenantId).setParameter("hsmId", hsmId).resultList.singleOrNull()

        private fun createAndPersistAssociation(
            entityManager: EntityManager,
            tenantId: String,
            hsmId: String,
            masterKeyPolicy: MasterKeyPolicy
        ): HSMAssociationEntity {
            val association = HSMAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                hsmId = hsmId,
                timestamp = Instant.now(),
                masterKeyAlias = if (masterKeyPolicy == MasterKeyPolicy.UNIQUE) {
                    generateRandomShortAlias()
                } else {
                    null
                }
            )

            entityManager.persist(association)
            return association
        }

        private fun generateRandomShortAlias() =
            toHex(UUID.randomUUID().toString().toByteArray()).take(12)

        private fun HSMCategoryAssociationEntity.toHSMAssociation() = HSMAssociationInfo(
            id,
            hsmAssociation.tenantId,
            hsmAssociation.hsmId,
            category,
            hsmAssociation.masterKeyAlias,
            deprecatedAt
        )

        private fun entityManagerFactory() = connectionsFactory.getEntityManagerFactory(CryptoTenants.CRYPTO)
    }
}