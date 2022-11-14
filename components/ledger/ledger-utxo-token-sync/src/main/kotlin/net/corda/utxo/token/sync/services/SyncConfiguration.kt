package net.corda.utxo.token.sync.services

import net.corda.libs.configuration.SmartConfig

/**
 * The [SyncConfiguration] represents the configurations settings for the Token Synchronization process
 *
 * @property minDelayBeforeNextFullSync The delay period before a new full sync can run, after a previous
 * Full Sync completed, in seconds
 * @property minDelayBeforeNextPeriodicSync The delay period before a new periodic sync check will run in seconds
 * @property fullSyncBlockSize The max number of records to retrieve for a full synchronization block
 * @property periodCheckBlockSize The max number of records to retrieve for a periodic synchronization block
 * @property sendWakeUpMaxRetryAttempts The max number wake-up on component startup retries before the process fails
 * @property sendWakeUpMaxRetryDelay The delay between retrying the wake-up on component startup in seconds
 */
interface SyncConfiguration {
    val minDelayBeforeNextFullSync: Long

    val minDelayBeforeNextPeriodicSync: Long

    val fullSyncBlockSize: Int

    val periodCheckBlockSize: Int

    val sendWakeUpMaxRetryAttempts: Int

    val sendWakeUpMaxRetryDelay: Long

    /**
     * Receives a change in configuration.
     *
     * When called this method update the current configuration values.
     *
     * @param config the latest platform configuration
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}
