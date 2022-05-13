package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.hsm.HSMCacheActions
import net.corda.crypto.persistence.hsm.HSMConfig
import net.corda.crypto.persistence.hsm.HSMStat
import net.corda.crypto.persistence.hsm.HSMTenantAssociation
import net.corda.crypto.persistence.db.impl.doInTransaction
import net.corda.crypto.persistence.db.model.HSMAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.HSMCategoryMapEntity
import net.corda.crypto.persistence.db.model.HSMConfigEntity
import net.corda.crypto.persistence.db.model.PrivateKeyPolicy
import net.corda.data.crypto.wire.hsm.HSMCategoryInfo
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.Tuple
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

@Suppress("TooManyFunctions")
class HSMCacheActionsImpl(
    private val entityManager: EntityManager
) : HSMCacheActions {
    private val secureRandom = SecureRandom()

    override fun findConfig(configId: String): HSMConfig? =
        entityManager.find(HSMConfigEntity::class.java, configId)?.toHSMConfig()

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
            result[0].toHSMTenantAssociation()
        }
    }

    override fun findTenantAssociation(associationId: String): HSMTenantAssociation? =
        entityManager.find(HSMCategoryAssociationEntity::class.java, associationId)?.toHSMTenantAssociation()

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
                    c.id as configId,
                    c.serviceName as serviceName,
                    c.capacity as capacity,
                    m.keyPolicy as privateKeyPolicy
            FROM HSMConfigEntity c
                    JOIN HSMCategoryMapEntity m ON m.config = c AND m.category = :category
            """.trimIndent(),
            Tuple::class.java
        ).setParameter("category", category).resultList.map {
            HSMStat(
                usages = (it.get("usages") as Number).toInt(),
                configId = it.get("configId") as String,
                serviceName = it.get("serviceName") as String,
                privateKeyPolicy = net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.valueOf(
                    (it.get("privateKeyPolicy") as PrivateKeyPolicy).name
                ),
                capacity = (it.get("capacity") as Number).toInt(),
            )
        }
    }

    override fun getLinkedCategories(configId: String): List<HSMCategoryInfo> {
        val config = getExistingHSMConfigEntity(configId)
        return entityManager.createQuery(
            """
            SELECT m FROM HSMCategoryMapEntity m WHERE m.config=:config
        """.trimIndent(), HSMCategoryMapEntity::class.java
        ).setParameter("config", config).resultList.map {
            HSMCategoryInfo(
                it.category,
                net.corda.data.crypto.wire.hsm.PrivateKeyPolicy.valueOf(it.keyPolicy.name)
            )
        }
    }

    override fun linkCategories(configId: String, links: List<HSMCategoryInfo>) {
        val config = getExistingHSMConfigEntity(configId)
        val map = links.map {
            HSMCategoryMapEntity(
                id = UUID.randomUUID().toString(),
                category = it.category,
                keyPolicy = PrivateKeyPolicy.valueOf(it.keyPolicy.name),
                timestamp = Instant.now(),
                config = config
            )
        }
        entityManager.doInTransaction {
            entityManager.createQuery("""
                DELETE FROM HSMCategoryMapEntity m WHERE m.config = :config
            """.trimIndent()).setParameter("config", config).executeUpdate()
            map.forEach { m ->
                it.persist(m)
            }
        }
    }

    override fun add(info: HSMInfo, serviceConfig: ByteArray): String {
        val config = info.toHSMConfigEntity(serviceConfig)
        entityManager.doInTransaction {
            it.persist(config)
        }
        return config.id
    }

    override fun merge(info: HSMInfo, serviceConfig: ByteArray) {
        val config = info.toHSMConfigEntity(serviceConfig)
        entityManager.doInTransaction {
            it.merge(config)
        }
    }

    override fun associate(tenantId: String, category: String, configId: String): HSMTenantAssociation {
        val association = findHSMAssociationEntity(tenantId, configId)
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

    private fun getExistingHSMConfigEntity(configId: String): HSMConfigEntity =
        (entityManager.find(HSMConfigEntity::class.java, configId)
            ?: throw CryptoServiceLibraryException("The HSM with id=$configId does not exists."))

    private fun generateRandomShortAlias() =
        UUID.randomUUID().toString().toByteArray().toHex().take(12)

    private fun HSMConfigEntity.toHSMConfig() = HSMConfig(
        toHSMInfo(),
        serviceConfig
    )

    private fun HSMInfo.toHSMConfigEntity(serviceConfig: ByteArray) = HSMConfigEntity(
        id = if(id.isNullOrBlank()) UUID.randomUUID().toString() else id,
        timestamp = Instant.now(),
        workerLabel = workerLabel,
        description = description,
        masterKeyPolicy = net.corda.crypto.persistence.db.model.MasterKeyPolicy.valueOf(masterKeyPolicy.name),
        masterKeyAlias = masterKeyAlias,
        supportedSchemes = supportedSchemes.joinToString(","),
        retries = retries,
        timeoutMills = timeoutMills,
        serviceName = serviceName,
        capacity = capacity,
        serviceConfig = serviceConfig
    )

    private fun HSMConfigEntity.toHSMInfo() = HSMInfo(
        id,
        timestamp,
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

    private fun HSMCategoryAssociationEntity.toHSMTenantAssociation() = HSMTenantAssociation(
        id = id,
        tenantId = hsm.tenantId,
        category = category,
        masterKeyAlias = hsm.masterKeyAlias,
        aliasSecret = hsm.aliasSecret,
        config = hsm.config.toHSMConfig()
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