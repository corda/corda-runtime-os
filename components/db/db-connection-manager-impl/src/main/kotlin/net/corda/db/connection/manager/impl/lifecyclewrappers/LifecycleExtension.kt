package net.corda.db.connection.manager.impl.lifecyclewrappers

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus

fun <T> LifecycleCoordinator.downOnError(lambda: () -> T): T = try {
    lambda()
} catch (e: Exception) {
    updateStatus(LifecycleStatus.DOWN, "Exception during operation")
    throw e
}
