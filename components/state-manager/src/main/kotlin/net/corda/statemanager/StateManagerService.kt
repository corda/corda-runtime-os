package net.corda.statemanager

import java.util.function.Supplier
import net.corda.libs.statemanager.StateManager
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

interface StateManagerService : Lifecycle {
    val lifecycleCoordinatorName: LifecycleCoordinatorName
    val stateManager: Supplier<StateManager?>
}