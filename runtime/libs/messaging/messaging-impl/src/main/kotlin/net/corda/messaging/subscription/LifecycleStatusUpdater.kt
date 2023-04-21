package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleStatus

interface LifecycleStatusUpdater {
    fun updateLifecycleStatus(newStatus: LifecycleStatus)
    fun updateLifecycleStatus(newStatus: LifecycleStatus, reason: String)
}
