package net.corda.utxo.token.sync.entities

import net.corda.lifecycle.TimerEvent
import net.corda.utxo.token.sync.TokenCacheSyncServiceComponent

/**
 * Lifecycle event generated when the [TokenCacheSyncServiceComponent] first starts.
 *
 * This event is used to generate a set of sync process wakeup events when the component first starts. These events are
 * used to kick-start the token synchronization process.
 *
 * @property key The timer key that generated this event.
 * @property attempts tracks the number of current attempts made to send a set of sync wake-ups.
 */
data class SendSyncWakeUpEvent(override val key: String, val attempts: Int) : TimerEvent
