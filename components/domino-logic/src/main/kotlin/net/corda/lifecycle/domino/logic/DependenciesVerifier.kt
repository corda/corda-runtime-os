package net.corda.lifecycle.domino.logic

import java.lang.IllegalStateException

class DependenciesVerifier {

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
        val allHierarchyTiles = mutableSetOf<DominoTile>()
        val managedTileToParent = mutableMapOf<DominoTile, DominoTile>()

        visit(rootDominoTile, allHierarchyTiles, managedTileToParent)

        allHierarchyTiles.forEach {
            if (!managedTileToParent.containsKey(it)) {
                throw InvalidTileConfigurationException("The domino tile ${it.name} is not being managed by any parent tile.")
            }
        }
    }

    private fun visit(dominoTile: DominoTile, allTiles: MutableSet<DominoTile>, managedTiles: MutableMap<DominoTile, DominoTile>) {
        val tilesNotSeenYet = dominoTile.dependentChildren.filter { !allTiles.contains(it) }
        allTiles.addAll(tilesNotSeenYet)

        dominoTile.managedChildren.forEach {
            if (managedTiles.contains(it)) {
                throw InvalidTileConfigurationException("The domino tile ${it.name} is being managed by two parent tiles: ${managedTiles[it]!!.name} and ${dominoTile.name}")
            }

            managedTiles[it] = dominoTile
        }

        tilesNotSeenYet.forEach { visit(it, allTiles, managedTiles) }
    }

    class InvalidTileConfigurationException(msg: String): IllegalStateException(msg)

}