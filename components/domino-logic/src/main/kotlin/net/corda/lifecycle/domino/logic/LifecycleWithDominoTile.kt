package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle

interface LifecycleWithDominoTile : Lifecycle {

    val complexDominoTile: ComplexDominoTile

    override val isRunning: Boolean
        get() = complexDominoTile.isRunning

    override fun start() {
        complexDominoTile.start()
    }

    override fun stop() {
        complexDominoTile.stop()
    }
}
