package net.corda.utxo.token.sync.services

import net.corda.libs.configuration.SmartConfig

interface WakeUpGeneratorService {
    fun onConfigChange(config: Map<String, SmartConfig>)

    fun isWakeUpRequired(): Boolean

    fun generateWakeUpEvents()
}
