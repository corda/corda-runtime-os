package net.corda.crypto.softhsm.impl

import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.HSMUsage
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.softhsm.HSMRepository
import net.corda.data.crypto.wire.hsm.HSMAssociationInfo
import net.corda.orm.utils.transaction
import net.corda.orm.utils.use
import net.corda.utilities.debug
import net.corda.utilities.toBytes
import net.corda.v5.base.util.EncodingUtils.toHex
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.PersistenceException
import javax.persistence.Tuple

/**
 * Database operations for HSM.
 *
 * @param entityManagerFactory An entity manager factory connected to the right database, matching tenantId
 */

class HSMRepositoryImpl(
    private val entityManagerFactory: EntityManagerFactory
) : HSMRepository {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun close() = entityManagerFactory.close()

    override fun findTenantAssociation(tenantId: String, category: String): HSMAssociationInfo? =
        entityManagerFactory.createEntityManager().use {
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
        }.also {
            logger.debug { "Looked up tenant association for $tenantId $category with wrapping key alias ${it?.masterKeyAlias}" }
        }

    override fun getHSMUsage(): List<HSMUsage> = entityManagerFactory.createEntityManager().use {
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

    override fun associate(
        tenantId: String,
        category: String,
        masterKeyPolicy: MasterKeyPolicy,
    ): HSMAssociationInfo = entityManagerFactory.createEntityManager().use {
        it.transaction { em ->
            val association =
                findHSMAssociationEntity(em, tenantId)
                    ?: createAndPersistAssociation(em, tenantId, CryptoConsts.SOFT_HSM_ID, masterKeyPolicy)

            val categoryAssociation = HSMCategoryAssociationEntity(
                id = UUID.randomUUID().toString(),
                tenantId = tenantId,
                category = category,
                timestamp = Instant.now(),
                hsmAssociation = association,
                deprecatedAt = 0
            )

            em.merge(categoryAssociation).toHSMAssociation()
        }.also {
            logger.trace("Stored tenant association $tenantId $category with wrapping key alias ${it.masterKeyAlias}")
        }
    }

    override fun createOrLookupCategoryAssociation(
        tenantId: String,
        category: String,
        masterKeyPolicy: MasterKeyPolicy
    ): HSMAssociationInfo = entityManagerFactory.createEntityManager().use {
        try {
            it.transaction { em ->
                val association =
                    findHSMAssociationEntity(em, tenantId)
                        ?: createAndPersistAssociation(em, tenantId, CryptoConsts.SOFT_HSM_ID, masterKeyPolicy)

                val tenantAssociation = findTenantAssociation(tenantId, category)
                if (tenantAssociation == null) {
                    val newAssociation = HSMCategoryAssociationEntity(
                        id = UUID.randomUUID().toString(),
                        tenantId = tenantId,
                        category = category,
                        timestamp = Instant.now(),
                        hsmAssociation = association,
                        deprecatedAt = 0
                    )
                    em.merge(newAssociation).toHSMAssociation().also {
                        logger.trace(
                            "Stored tenant category association $tenantId $category with wrapping key alias " +
                                    it.masterKeyAlias
                        )
                    }
                } else {
                    logger.trace(
                        "Reusing tenant category association $tenantId $category with wrapping key alias " +
                                tenantAssociation.masterKeyAlias
                    )
                    tenantAssociation
                }
            }
        } catch (e: PersistenceException) {
            val match = e.cause?.message?.contains("ConstraintViolationException") ?: false
            // NOTE: this is not great, but we must be able to detect a constraint violation in case
            //  of a race condition, however, the JPA exception type doesn't give us enough info, so we check
            //  the hibernate generated message.
            if (match) {
                findTenantAssociation(tenantId, category)
                    ?: throw IllegalStateException("unable to find tenant assocation $tenantId:$category after constraint violation")
            } else {
                throw e
            }
        }
    }

    private fun findHSMAssociationEntity(
        entityManager: EntityManager,
        tenantId: String
    ) =
        entityManager.createQuery(
            """
            SELECT a 
            FROM HSMAssociationEntity a
            WHERE a.tenantId = :tenantId AND a.hsmId = :hsmId
            """.trimIndent(),
            HSMAssociationEntity::class.java
        ).setParameter("tenantId", tenantId).setParameter("hsmId", CryptoConsts.SOFT_HSM_ID).resultList.singleOrNull()

    private fun createAndPersistAssociation(
        entityManager: EntityManager,
        tenantId: String,
        hsmId: String,
        masterKeyPolicy: MasterKeyPolicy,
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
        logger.info("Making new association tenant $tenantId wrapping key alias ${association.masterKeyAlias}")
        return association
    }

    private fun generateRandomShortAlias() =
        toHex(UUID.randomUUID().toBytes()).take(12)
}

// NOTE: this should be on the Entity.
internal fun HSMCategoryAssociationEntity.toHSMAssociation() = HSMAssociationInfo(
    id,
    hsmAssociation.tenantId,
    hsmAssociation.hsmId,
    category,
    hsmAssociation.masterKeyAlias,
    deprecatedAt
)