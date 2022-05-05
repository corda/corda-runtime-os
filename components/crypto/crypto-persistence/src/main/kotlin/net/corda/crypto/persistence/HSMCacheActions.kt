package net.corda.crypto.persistence

import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.data.crypto.wire.hsm.HSMInfo

interface HSMCacheActions : AutoCloseable {
    fun findConfig(configId: String): HSMConfig?

    fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation?

    fun lookup(filter: Map<String, String>): List<HSMInfo>

    fun getHSMStats(category: String): List<HSMStat>

    /**
     * Replaces the category to HSM configuration mappings.
     */
    fun linkCategories(configId: String, links: Set<HSMCategoryInfo>)

    /**
     * @return the id of the new configuration.
     */
    fun add(info: HSMInfo, serviceConfig: ByteArray): String

    fun merge(info: HSMInfo, serviceConfig: ByteArray)

    fun associate(tenantId: String, category: String, configId: String): HSMTenantAssociation
}