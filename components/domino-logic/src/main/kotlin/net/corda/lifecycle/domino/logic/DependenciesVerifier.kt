package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinatorName
import java.lang.IllegalStateException

object DependenciesVerifier {

    /**
     * Verifies that wiring of tile dependencies is valid.
     *
     * For example:
     * - each tile that exists in the hierarchy (as a dependent child of some other tile) is also a managed child of some tile.
     * - no tile is being managed by more than one tiles.
     *
     * @throws InvalidTileConfigurationException if the configuration is invalid.
     */
    fun verify(rootDominoTile: DominoTile) {
        val managedTiles = mutableSetOf<DominoTile>()
        val dependantTiles =  mutableSetOf<LifecycleCoordinatorName>()

        visit(rootDominoTile, managedTiles, dependantTiles)

        dependantTiles.forEach { dependentTile ->
            if (!managedTiles.map { it.coordinatorName }.contains(dependentTile)) {
                throw InvalidTileConfigurationException("The domino tile $dependentTile is not being managed by any parent tile.")
            }
        }
    }

    private fun visit(dominoTile: DominoTile, managedTiles: MutableSet<DominoTile>, dependantTiles: MutableSet<LifecycleCoordinatorName>) {
        dominoTile.managedChildren.forEach {
            if (managedTiles.contains(it)) {
                throw InvalidTileConfigurationException("The domino tile ${it.coordinatorName} is being managed by two parent tiles: " +
                        "${it.coordinatorName} and ${dominoTile.coordinatorName}")
            }
            managedTiles.add(it)
            visit(it, managedTiles, dependantTiles)
        }
        dependantTiles.addAll(dominoTile.dependentChildren)
    }

}

class InvalidTileConfigurationException(msg: String): IllegalStateException(msg)