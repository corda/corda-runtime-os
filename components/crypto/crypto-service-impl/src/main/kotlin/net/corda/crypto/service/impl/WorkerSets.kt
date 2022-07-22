package net.corda.crypto.service.impl

import net.corda.crypto.config.impl.CryptoWorkerSetConfig
import net.corda.crypto.config.impl.MasterKeyPolicy
import net.corda.crypto.config.impl.workerSets
import net.corda.crypto.core.CryptoConsts.SOFT_HSM_WORKER_SET_ID
import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.libs.configuration.SmartConfig

class WorkerSets(
    cryptoConfig: SmartConfig,
    private val store: HSMStore
) {
    private val sets: Map<String, CryptoWorkerSetConfig> = cryptoConfig.workerSets()

    val isOnlySoftHSM: Boolean get() = sets.size == 1 && sets.keys.first() == SOFT_HSM_WORKER_SET_ID

    fun getHSMStats(category: String): List<HSMStats> {
        val usages = store.getHSMUsage()
        return sets.filter {
            it.key != SOFT_HSM_WORKER_SET_ID && it.value.hsm.categories.any { c -> c.category == category }
        }.map {
            HSMStats(
                workerSetId = it.key,
                allUsages = usages.firstOrNull { u -> u.workerSetId == it.key }?.usages ?: 0,
                privateKeyPolicy = it.value.hsm.categories.first { c -> c.category == category }.policy,
                capacity = it.value.hsm.capacity
            )
        }
    }

    fun getMasterKeyPolicy(workerSetId: String): MasterKeyPolicy =
        sets.getValue(workerSetId).hsm.masterKeyPolicy
}