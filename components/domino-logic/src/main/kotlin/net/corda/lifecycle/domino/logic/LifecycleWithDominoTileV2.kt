package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle

interface LifecycleWithDominoTileV2 : Lifecycle {

    val dominoTile: DominoTileV2

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        dominoTile.start()
    }

    override fun stop() {
        dominoTile.stop()
    }
}
