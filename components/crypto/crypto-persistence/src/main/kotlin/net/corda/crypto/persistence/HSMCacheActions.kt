package net.corda.crypto.persistence

import net.corda.data.crypto.wire.hsm.HSMInfo

interface HSMCacheActions : AutoCloseable {
    fun exists(configId: String): Boolean
    fun add(info: HSMInfo, serviceConfig: ByteArray)
    fun merge(info: HSMInfo, serviceConfig: ByteArray)
    fun findTenantAssociation(tenantId: String, category: String): HSMTenantAssociation?
}