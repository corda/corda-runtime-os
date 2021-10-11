package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.ResourcesHolder

abstract class LeafTile(
    coordinatorFactory: LifecycleCoordinatorFactory
) :
    DominoTile(coordinatorFactory) {

    protected val resources = ResourcesHolder()

    override fun startTile() {
        @Suppress("TooGenericExceptionCaught")
        try {
            createResources()
        } catch (e: Throwable) {
            gotError(e)
        }
    }

    abstract fun createResources()

    override fun stopTile() {
        resources.close()
    }
}
