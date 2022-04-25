package net.corda.crypto.service

import net.corda.data.crypto.config.HSMConfig
import net.corda.data.crypto.config.TenantHSMConfig
import net.corda.lifecycle.Lifecycle

interface HSMRegistration : Lifecycle {
    fun getHSMConfig(id: String): HSMConfig
    fun getTenantConfig(tenantId: String, category: String): TenantHSMConfig
}