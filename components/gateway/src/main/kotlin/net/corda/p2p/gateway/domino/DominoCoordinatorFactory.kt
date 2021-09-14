package net.corda.p2p.gateway.domino

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName

class DominoCoordinatorFactory(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val dominoCoordinatorInstanceId: String,
) {
    fun createFor(tile: DominoTile): LifecycleCoordinator {
        val name = LifecycleCoordinatorName(
            tile.javaClass.canonicalName,
            dominoCoordinatorInstanceId
        )
        return coordinatorFactory.createCoordinator(name, tile)
    }
}
