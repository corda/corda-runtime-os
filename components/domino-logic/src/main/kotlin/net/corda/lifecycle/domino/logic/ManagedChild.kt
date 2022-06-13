package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator

data class ManagedChild(val coordinator: LifecycleCoordinator, val lifecycle: Lifecycle)

//fun DominoTile.toManagedChild(): ManagedChild {
//    return ManagedChild(this.coordinator, this)
//}