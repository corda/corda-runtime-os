package net.corda.schema.configuration

object LedgerConfig {
    const val UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_FULL_SYNC = "tokens.minDelayBeforeNextFullSync"
    const val UTXO_TOKEN_MIN_DELAY_BEFORE_NEXT_PERIODIC_SYNC = "tokens.minDelayBeforeNextPeriodicSync"
    const val UTXO_TOKEN_FULL_SYNC_BLOCK_SIZE = "tokens.fullSyncBlockSize"
    const val UTXO_TOKEN_PERIODIC_CHECK_BLOCK_SIZE = "tokens.periodCheckBlockSize"
    const val UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_ATTEMPTS = "tokens.sendWakeUpMaxRetryAttempts"
    const val UTXO_TOKEN_SEND_WAKEUP_MAX_RETRY_DELAY = "tokens.sendWakeUpMaxRetryDelay"
}
