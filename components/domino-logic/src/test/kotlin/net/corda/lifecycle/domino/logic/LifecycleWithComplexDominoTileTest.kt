package net.corda.lifecycle.domino.logic

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LifecycleWithComplexDominoTileTest {
    val tile = mock<DominoTile>()
    val lifecycle = object : LifecycleWithDominoTile {
        override val dominoTile = tile
    }

    @Test
    fun `isRunning return tile state`() {
        whenever(tile.isRunning).doReturn(true)

        assertThat(lifecycle.isRunning).isTrue
    }

    @Test
    fun `start will start the tile`() {
        lifecycle.start()

        verify(tile).start()
    }

    @Test
    fun `stop will stop the tile`() {
        lifecycle.stop()

        verify(tile).stop()
    }
}
