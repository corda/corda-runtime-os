package net.corda.crypto.service.impl

import net.corda.crypto.config.impl.CryptoHSMConfig
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_ID
import net.corda.crypto.persistence.HSMStore
import net.corda.libs.configuration.SmartConfig

class HSMMap(
    cryptoConfig: SmartConfig,
    private val store: HSMStore
) {
    private val hsms: Map<String, CryptoHSMConfig> = cryptoConfig.hsmMap()

    val isOnlySoftHSM: Boolean get() = hsms.size == 1 && hsms.keys.first() == SOFT_HSM_ID

    fun getHSMStats(category: String): List<HSMStats> {
        val usages = store.getHSMUsage()
        return hsms.filter {
            it.key != SOFT_HSM_ID && it.value.hsm.categories.any { c ->
                c.category == category || c.category == "*"
            }
        }.map {
            HSMStats(
                hsmId = it.key,
                allUsages = usages.firstOrNull { u -> u.hsmId == it.key }?.usages ?: 0,
                privateKeyPolicy = it.value.hsm.categories.first { c ->
                    c.category == category || c.category == "*"
                }.policy,
                capacity = it.value.hsm.capacity
            )
        }
    }

    fun getMasterKeyPolicy(hsmId: String): MasterKeyPolicy =
        hsms.getValue(hsmId).hsm.masterKeyPolicy
}