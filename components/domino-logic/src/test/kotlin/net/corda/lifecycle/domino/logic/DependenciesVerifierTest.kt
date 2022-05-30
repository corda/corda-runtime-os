package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DependenciesVerifierTest {

    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn mock()
    }

    @Test
    fun `verification fails if a dependent tile is not managed by any tile`() {
        /**
         * Scenario with the structure below, where A is managed by both B and C.
         *      D
         *    B   C
         *   A     A
         */
        val tileA = tile("A")
        val tileB = tile("B", listOf(tileA.coordinatorName), listOf(tileA))
        val tileC = tile("C", listOf(tileA.coordinatorName), listOf(tileA))
        val tileD = tile("D", listOf(tileB.coordinatorName, tileC.coordinatorName), listOf(tileB, tileC))

        assertThatThrownBy {
            DependenciesVerifier.verify(tileD)
        }.isInstanceOf(InvalidTileConfigurationException::class.java)
         .hasMessageContaining("being managed by two parent tiles")
    }

    @Test
    fun `verification fails if a tile is managed by more than one tiles`() {
        /**
         * Scenario with the structure below, where A is not managed by any tile.
         *      D
         *    B   C
         *   A     A
         */
        val tileA = tile("A")
        val tileB = tile("B", listOf(tileA.coordinatorName))
        val tileC = tile("C", listOf(tileA.coordinatorName))
        val tileD = tile("D", listOf(tileB.coordinatorName, tileC.coordinatorName), listOf(tileB, tileC))

        assertThatThrownBy {
            DependenciesVerifier.verify(tileD)
        }.isInstanceOf(InvalidTileConfigurationException::class.java)
            .hasMessageContaining("is not being managed by any parent tile")
    }

    @Test
    fun `verification succeeds if all tiles are managed by exactly one parent tile`() {
        /**
         * Scenario with the structure below, where A managed only by B.
         *      D
         *    B   C
         *   A     A
         */
        val tileA = tile("A")
        val tileB = tile("B", listOf(tileA.coordinatorName), listOf(tileA))
        val tileC = tile("C", listOf(tileA.coordinatorName))
        val tileD = tile("D", listOf(tileB.coordinatorName, tileC.coordinatorName), listOf(tileB, tileC))

        assertDoesNotThrow {
            DependenciesVerifier.verify(tileD)
        }
    }

    private fun tile(name: String,
                     dependentChildren: Collection<LifecycleCoordinatorName> = emptyList(),
                     managedChildren: Collection<ComplexDominoTile> = emptyList()): ComplexDominoTile {
        return ComplexDominoTile(
            name,
            lifecycleCoordinatorFactory,
            mock(),
            dependentChildren =  dependentChildren,
            managedChildren = managedChildren
        )
    }

}