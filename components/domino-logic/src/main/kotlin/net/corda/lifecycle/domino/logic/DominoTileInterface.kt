package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

interface DominoTileInterface: Lifecycle {
    val coordinatorName: LifecycleCoordinatorName
    val state: DominoTileState
    val dependentChildren: Collection<DominoTileInterface>
    val managedChildren: Collection<DominoTileInterface>
}

enum class DominoTileState {
    Created,
    Started,
    StoppedDueToError,
    StoppedDueToBadConfig,
    StoppedDueToChildStopped,
    StoppedByParent
}
class StatusChangeEvent(val newState: DominoTileState)