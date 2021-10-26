package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.IOException

class LeafTileTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { postEvent(any()) } doAnswer {
            handler.lastValue.processEvent(it.getArgument(0) as LifecycleEvent, mock)
        }
    }
    private val factory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }

    @Test
    fun `startTile called createResource`() {
        var createResourceCalled = 0
        val tile = object : LeafTile(factory) {
            override fun createResources() {
                createResourceCalled ++
            }
        }

        tile.start()

        assertThat(createResourceCalled).isEqualTo(1)
    }

    @Test
    fun `startTile will set error if created resource failed`() {
        val tile = object : LeafTile(factory) {
            override fun createResources() {
                throw IOException("")
            }
        }

        tile.start()

        assertThat(tile.state).isEqualTo(DominoTile.State.StoppedDueToError)
    }

    @Test
    fun `stopTile will close all the resources`() {
        val actions = mutableListOf<Int>()
        val tile = object : LeafTile(factory) {
            override fun createResources() {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    actions.add(2)
                }
                resources.keep {
                    actions.add(3)
                }
            }

            override fun start() {
                stopTile(false)
            }
        }
        tile.createResources()

        tile.stop()

        assertThat(actions).isEqualTo(listOf(3, 2, 1))
    }

    @Test
    fun `stopTile will ignore error during stop`() {
        val actions = mutableListOf<Int>()
        val tile = object : LeafTile(factory) {
            override fun createResources() {
                resources.keep {
                    actions.add(1)
                }
                resources.keep {
                    throw IOException("")
                }
                resources.keep {
                    actions.add(3)
                }
            }

            override fun start() {
                stopTile(false)
            }
        }
        tile.createResources()

        tile.stop()

        assertThat(actions).isEqualTo(listOf(3, 1))
    }

    @Test
    fun `stopTile will not run the same actions again`() {
        val actions = mutableListOf<Int>()
        val tile = object : LeafTile(factory) {
            override fun createResources() {
                resources.keep {
                    actions.add(1)
                }
            }

            override fun start() {
                stopTile(false)
            }
        }

        tile.createResources()
        tile.stop()
        tile.stop()
        tile.stop()

        assertThat(actions).isEqualTo(listOf(1))
    }

    @Test
    fun `handleEvent can handle unknown event`() {
        val tile = object : LeafTile(factory) {
            override fun createResources() {
            }
        }
        val event = RegistrationStatusChangeEvent(
            mock(),
            LifecycleStatus.UP
        )
        tile.start()

        assertDoesNotThrow {
            handler.lastValue.processEvent(event, coordinator)
        }
    }
}
