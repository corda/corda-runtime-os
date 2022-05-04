package net.corda.crypto.persistence.db.impl.hsm

import net.corda.crypto.persistence.HSMCacheActions
import net.corda.crypto.persistence.HSMStat
import net.corda.crypto.persistence.HSMTenantAssociation
import net.corda.crypto.persistence.db.model.HSMCategoryAssociationEntity
import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo
import net.corda.data.crypto.wire.hsm.MasterKeyPolicy
import java.nio.ByteBuffer
import javax.persistence.EntityManager

class HSMCacheActionsImpl(
    private val entityManager: EntityManager
) : HSMCacheActions {
    override fun findConfig(configId: String): HSMConfig? {
        TODO("Not yet implemented")
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
        return if(result.isEmpty()) {
            null
        } else {
            result[0].let {
                HSMTenantAssociation(
                    tenantId = tenantId,
                    category = category,
                    masterKeyAlias = it.hsm.masterKeyAlias,
                    aliasSecret = it.hsm.aliasSecret,
                    config = HSMConfig(
                        HSMInfo(
                            it.hsm.config.id,
                            it.hsm.config.timestamp,
                            it.hsm.config.version,
                            it.hsm.config.workerLabel,
                            it.hsm.config.description,
                            MasterKeyPolicy.valueOf(it.hsm.config.masterKeyPolicy.name),
                            it.hsm.config.masterKeyAlias,
                            it.hsm.config.retries,
                            it.hsm.config.timeoutMills,
                            it.hsm.config.supportedSchemes.split(","),
                            it.hsm.config.serviceName,
                            it.hsm.config.capacity
                        ),
                        ByteBuffer.wrap(it.hsm.config.serviceConfig)
                    )
                )
            }
        }
    }

    override fun lookup(): List<HSMInfo> {
        TODO("Not yet implemented")
    }

    override fun getHSMStats(category: String): List<HSMStat> {
        TODO("Not yet implemented")
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
}