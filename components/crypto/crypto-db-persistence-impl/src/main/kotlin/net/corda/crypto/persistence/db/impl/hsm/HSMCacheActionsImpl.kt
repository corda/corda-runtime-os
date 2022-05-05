package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.HSMCacheActions
import net.corda.crypto.persistence.HSMStat
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.HSMConfigEntity
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction
import javax.persistence.PersistenceException
import javax.persistence.Tuple
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

class HSMCacheActionsImpl(
    private val entityManager: EntityManager
) : HSMCacheActions {
    private val secureRandom = SecureRandom()

    override fun findConfig(configId: String): HSMConfig? =
        entityManager.find(HSMConfigEntity::class.java, configId)?.let {
            it.toHSMConfig()
        }

    override fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation? {
        val result = entityManager.createQuery(
            """
            SELECT ca FROM HSMCategoryAssociationEntity ca
                JOIN FETCH ca.hsm a
                JOIN FETCH a.config c
            WHERE ca.category = :category 
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
            result[0].let {
                HSMTenantAssociation(
                    tenantId = tenantId,
                    category = category,
                    masterKeyAlias = it.hsm.masterKeyAlias,
                    aliasSecret = it.hsm.aliasSecret,
                    config = it.hsm.config.toHSMConfig()
                )
            }
        }
    }

    override fun lookup(filter: Map<String, String>): List<HSMInfo> {
        val builder = LookupBuilder(entityManager)
        builder.equal(HSMConfigEntity::serviceName, filter[CryptoConsts.HSMFilters.SERVICE_NAME_FILTER])
        return builder.build().resultList.map {
            it.toHSMInfo()
        }
    }

    override fun getHSMStats(category: String): List<HSMStat> {
        return entityManager.createQuery(
            """
            SELECT DISTINCT
                    (SELECT COUNT(*) FROM HSMCategoryAssociationEntity ca JOIN ca.hsm a
                        WHERE EXISTS(
                            SELECT 1 FROM HSMCategoryMapEntity mm
                                WHERE mm.config = a.config AND (mm.keyPolicy = 'ALIASED' OR mm.keyPolicy = 'BOTH')
                        ) AND a.config = c
                    ) as usages,   
                    c.id as configId
            FROM HSMConfigEntity c
                WHERE EXISTS(SELECT 1 FROM HSMCategoryMapEntity m WHERE m.config = c AND m.category = :category)
            """.trimIndent(),
            Tuple::class.java
        ).setParameter("category", category).resultList.map {
            HSMStat((it.get("usages") as Number).toInt(), it.get("configId") as String)
        }
    }

    override fun add(info: HSMInfo, serviceConfig: ByteArray): String {
        TODO("Not yet implemented")
    }

    override fun merge(info: HSMInfo, serviceConfig: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun associate(tenantId: String, category: String, configId: String): HSMTenantAssociation {
        val association = entityManager.createQuery(
            """
                SELECT a FROM HSMAssociationEntity a JOIN a.config c
                WHERE a.tenantId = :tenantId AND c.id = :configId
            """.trimIndent(),
            HSMAssociationEntity::class.java
        )
            .setParameter("tenantId", tenantId)
            .setParameter("configId", configId)
            .resultList.singleOrNull()
            ?: createAndPersistAssociation(tenantId, configId)
        val categoryAssociation = HSMCategoryAssociationEntity(
            id = UUID.randomUUID().toString(),
            category = category,
            timestamp = Instant.now(),
            hsm = association
        )
        entityManager.doInTransaction {
            it.persist(categoryAssociation)
        }
        return HSMTenantAssociation(
            tenantId = tenantId,
            category = category,
            masterKeyAlias = association.masterKeyAlias,
            aliasSecret = association.aliasSecret,
            config = association.config.toHSMConfig()
        )
    }

    override fun close() {
        entityManager.close()
    }

    private fun createAndPersistAssociation(tenantId: String, configId: String): HSMAssociationEntity {
        val config = entityManager.find(HSMConfigEntity::class.java, configId)
            ?: throw CryptoServiceLibraryException("The HSM with id=$configId does not exists.")
        val aliasSecret = ByteArray(32)
        secureRandom.nextBytes(aliasSecret)
        val association = HSMAssociationEntity(
            id = UUID.randomUUID().toString(),
            tenantId = tenantId,
            config = config,
            timestamp = Instant.now(),
            masterKeyAlias = if(config.masterKeyPolicy == net.corda.crypto.persistence.db.model.MasterKeyPolicy.NEW) {
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

    private fun <R> EntityManager.doInTransaction(block: (EntityManager) -> R): R {
        val trx = entityManager.beginTransaction()
        try {
            val result = block(this)
            trx.commit()
            return result
        } catch (e: PersistenceException) {
            trx.safelyRollback()
            throw e
        } catch (e: Throwable) {
            trx.safelyRollback()
            throw PersistenceException("Failed to execute in transaction.", e)
        }
    }

    private fun EntityTransaction.safelyRollback() {
        try {
            rollback()
        } catch (e: Throwable) {
            // intentional
        }
    }

    private fun EntityManager.beginTransaction(): EntityTransaction {
        val trx = transaction
        trx.begin()
        return trx
    }

    private fun generateRandomShortAlias() =
        UUID.randomUUID().toString().toByteArray().toHex().take(12)

    private fun HSMConfigEntity.toHSMConfig() = HSMConfig(
        toHSMInfo(),
        ByteBuffer.wrap(serviceConfig)
    )

    private fun HSMConfigEntity.toHSMInfo() =
        HSMInfo(
            id,
            timestamp,
            version,
            workerLabel,
            description,
            MasterKeyPolicy.valueOf(masterKeyPolicy.name),
            masterKeyAlias,
            retries,
            timeoutMills,
            supportedSchemes.split(","),
            serviceName,
            capacity
        )

    private class LookupBuilder(
        private val entityManager: EntityManager
    ) {
        private val cb = entityManager.criteriaBuilder
        private val cr = cb.createQuery(HSMConfigEntity::class.java)
        private val root = cr.from(HSMConfigEntity::class.java)
        private val predicates = mutableListOf<Predicate>()

        fun <T> equal(property: KProperty<T?>, value: T?) {
            if (value != null) {
                predicates.add(cb.equal(root.get<T>(property.name), value))
            }
        }

        @Suppress("SpreadOperator", "ComplexMethod")
        fun build(): TypedQuery<HSMConfigEntity> {
            cr.where(cb.and(*predicates.toTypedArray()))
            return entityManager.createQuery(cr)
        }
    }
}