package net.corda.lifecycle.domino.logic

internal enum class DominoTileState {
    Created,
    Started,
    StoppedDueToError,
    StoppedDueToBadConfig,
    StoppedDueToChildStopped,
    StoppedByParent
}