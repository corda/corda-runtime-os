package net.corda.crypto.persistence.impl

import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMUsage
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.v5.base.util.toHex
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.Tuple

@Suppress("TooManyFunctions")
class HSMStoreImpl(
    private val entityManager: EntityManager
) : HSMStore {
    private val secureRandom = SecureRandom()

    override fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation? {
        val result = entityManager.createQuery(
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
        )
            .setParameter("category", category)
            .setParameter("tenantId", tenantId)
            .resultList
        return if (result.isEmpty()) {
            null
        } else {
            result[0].toHSMTenantAssociation()
        }
    }

    override fun findTenantAssociation(associationId: String): HSMTenantAssociation? =
        entityManager.find(HSMCategoryAssociationEntity::class.java, associationId)?.toHSMTenantAssociation()


    override fun getHSMUsage(category: String): List<HSMUsage> {
        return entityManager.createQuery(
            """
            SELECT DISTINCT
                    (SELECT COUNT(*) FROM HSMCategoryAssociationEntity ca JOIN ca.hsm a
                        WHERE EXISTS(
                            SELECT 1 FROM HSMCategoryMapEntity mm
                                WHERE mm.config = a.config AND (mm.keyPolicy = 'ALIASED' OR mm.keyPolicy = 'BOTH')
                        ) AND a.config = c
                    ) as usages,   
                    c.id as configId,
                    c.serviceName as serviceName,
                    c.capacity as capacity,
                    m.keyPolicy as privateKeyPolicy
            FROM HSMConfigEntity c
                    JOIN HSMCategoryMapEntity m ON m.config = c AND m.category = :category
            """.trimIndent(),
            Tuple::class.java
        ).setParameter("category", category).resultList.map {
            HSMUsage(
                usages = (it.get("usages") as Number).toInt(),
                workerSetId = it.get("configId") as String,
                serviceName = it.get("serviceName") as String,
                privateKeyPolicy = net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.valueOf(
                    (it.get("privateKeyPolicy") as PrivateKeyPolicy).name
                ),
                capacity = (it.get("capacity") as Number).toInt(),
            )
        }
    }

    override fun associate(tenantId: String, category: String, workerSetId: String): HSMTenantAssociation {
        val association = findHSMAssociationEntity(tenantId, workerSetId)
            ?: createAndPersistAssociation(tenantId, workerSetId)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            category = category,
            timestamp = Instant.now(),
            association = association,
            deprecatedAt = 0
        )
        entityManager.doInTransaction {
            it.persist(categoryAssociation)
        }
        return categoryAssociation.toHSMTenantAssociation()
    }

    override fun close() {
        entityManager.close()
    }

    private fun findHSMAssociationEntity(
        tenantId: String,
        configId: String
    ) = entityManager.createQuery(
        """
                    SELECT a FROM HSMAssociationEntity a JOIN a.config c
                    WHERE a.tenantId = :tenantId AND c.id = :configId
                """.trimIndent(),
        HSMAssociationEntity::class.java
    )
        .setParameter("tenantId", tenantId)
        .setParameter("configId", configId)
        .resultList.singleOrNull()

    private fun createAndPersistAssociation(tenantId: String, configId: String): HSMAssociationEntity {
        val config = getExistingHSMConfigEntity(configId)
        val aliasSecret = ByteArray(32)
        secureRandom.nextBytes(aliasSecret)
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            config = config,
            timestamp = Instant.now(),
            masterKeyAlias = if (config.masterKeyPolicy == net.corda.crypto.persistence.db.model.MasterKeyPolicy.NEW) {
                generateRandomShortAlias()
            } else {
                null
            },
            aliasSecret = aliasSecret
        )
        entityManager.doInTransaction {
            entityManager.persist(association)
        }
        return association
    }

    private fun generateRandomShortAlias() =
        UUID.randomUUID().toString().toByteArray().toHex().take(12)

    private fun HSMCategoryAssociationEntity.toHSMTenantAssociation() = HSMTenantAssociation(
        id = id,
        tenantId = association.tenantId,
        category = category,
        masterKeyAlias = association.masterKeyAlias,
        aliasSecret = association.aliasSecret,
        workerSetId = association.workerSetId,
        deprecatedAt = deprecatedAt
    )
}