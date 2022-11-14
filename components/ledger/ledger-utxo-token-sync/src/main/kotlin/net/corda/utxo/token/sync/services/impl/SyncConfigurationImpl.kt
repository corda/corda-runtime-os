package net.corda.utxo.token.sync.services.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.LedgerConfig
import net.corda.utxo.token.sync.services.SyncConfiguration

class SyncConfigurationImpl : SyncConfiguration {

    private var nullableSyncConfig: SmartConfig? = null

    override val minDelayBeforeNextFullSync: Long
        get() = syncConfig().getLong(LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_FULL_SYNC)

    override val minDelayBeforeNextPeriodicSync: Long
        get() = syncConfig().getLong(LedgerConfig.UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC)

    override val fullSyncBlockSize: Int
        get() = syncConfig().getInt(LedgerConfig.UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE)

    override val periodCheckBlockSize: Int
        get() = syncConfig().getInt(LedgerConfig.UTXO_TOKEN_PERIODIC_CHECK_BLOCK_SIZE)

    override val sendWakeUpMaxRetryAttempts: Int
        get() = syncConfig().getInt(LedgerConfig.UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_ATTEMPTS)

    override val sendWakeUpMaxRetryDelay: Long
        get() = syncConfig().getLong(LedgerConfig.UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_DELAY)

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        nullableSyncConfig = checkNotNull(config[ConfigKeys.UTXO_LEDGER_CONFIG])
        {
            "Could not find the ${ConfigKeys.UTXO_LEDGER_CONFIG} section in the received configuration "
        }
    }

    private fun syncConfig(): SmartConfig {
        return checkNotNull(nullableSyncConfig) {
            "The configuration has not been received"
        }
    }
}
