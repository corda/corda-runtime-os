package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.HSMCacheActions
import net.corda.crypto.persistence.HSMStat
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.crypto.persistence.db.model.HSMConfigEntity
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import java.nio.ByteBuffer
import javax.persistence.EntityManager
import javax.persistence.Tuple
import javax.persistence.TypedQuery
import javax.persistence.criteria.Predicate
import kotlin.reflect.KProperty

data class HSMStat2(
    val usages: Int,
    val configId: String
)

class HSMCacheActionsImpl(
    private val entityManager: EntityManager
) : HSMCacheActions {
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
                        )
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

    override fun associate(tenantId: String, category: String, hsm: HSMInfo): HSMTenantAssociation {
        TODO("Not yet implemented")
    }

    override fun close() {
        entityManager.close()
    }

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