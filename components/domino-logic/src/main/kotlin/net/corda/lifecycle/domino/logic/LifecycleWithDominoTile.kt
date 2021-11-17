package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle

interface LifecycleWithDominoTile : Lifecycle {

    val dominoTile: DominoTile

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        dominoTile.start()
    }

    override fun stop() {
        dominoTile.stop()
    }
}
