package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.util.ResourcesHolder

abstract class InternalTileWithResources(
    coordinatorFactory: LifecycleCoordinatorFactory
) :
    InternalTile(coordinatorFactory) {
    protected val resources = ResourcesHolder()
    protected abstract fun createResources()
    override fun started() {
        createResources()
        super.started()
    }

    override fun stopTile() {
        resources.close()
        super.stopTile()
    }
}
