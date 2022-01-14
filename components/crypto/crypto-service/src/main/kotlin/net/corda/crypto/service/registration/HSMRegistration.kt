package net.corda.crypto.service.registration

import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.TenantHSMConfig

interface HSMRegistration {
    fun getHSMConfig(id: String): HSMConfig
    fun getTenantConfig(tenantId: String, category: String): TenantHSMConfig
}