package net.corda.lifecycle.domino.logic

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
    fun verify(rootDominoTile: DominoTileInterface) {
        val allHierarchyTiles = mutableSetOf<DominoTileInterface>()
        val managedTileToParent = mutableMapOf<DominoTileInterface, DominoTileInterface>()

        visit(rootDominoTile, allHierarchyTiles, managedTileToParent)

        allHierarchyTiles.forEach {
            if (!managedTileToParent.containsKey(it)) {
                throw InvalidTileConfigurationException("The domino tile ${it.coordinatorName} is not being managed by any parent tile.")
            }
        }
    }

    private fun visit(dominoTile: DominoTileInterface, allTiles: MutableSet<DominoTileInterface>, managedTiles: MutableMap<DominoTileInterface, DominoTileInterface>) {
        val tilesNotSeenYet = dominoTile.dependentChildren.filter { !allTiles.contains(it) }
        allTiles.addAll(tilesNotSeenYet)

        dominoTile.managedChildren.forEach {
            if (managedTiles.contains(it)) {
                throw InvalidTileConfigurationException("The domino tile ${it.coordinatorName} is being managed by two parent tiles: " +
                        "${managedTiles[it]!!.coordinatorName} and ${dominoTile.coordinatorName}")
            }

            managedTiles[it] = dominoTile
        }

        tilesNotSeenYet.forEach { visit(it, allTiles, managedTiles) }
    }

}

class InvalidTileConfigurationException(msg: String): IllegalStateException(msg)