package net.corda.membership.synchronisation

import net.corda.data.membership.command.synchronisation.SynchronisationCommand
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

/**
 * Handles the data synchronisation processes on the member or mgm side.
 */
interface SynchronisationService : Lifecycle {
    /**
     * Lifecycle coordinator name for implementing services.
     *
     * All implementing services must make use of the optional `instanceId` parameter when creating their
     * [LifecycleCoordinatorName] so that multiple instances of [SynchronisationService] coordinator can be created
     * and followed by the synchronisation provider.
     */
    val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName(SynchronisationService::class.java.name, this::class.java.name)
}
