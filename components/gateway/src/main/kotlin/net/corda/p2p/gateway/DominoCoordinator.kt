package net.corda.p2p.gateway

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.TimerEvent

class DominoCoordinator(
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
) : Lifecycle, LifecycleEventHandler {
    private val name = LifecycleCoordinatorName("Gateway.Coordinator")
    private val coordinator = lifecycleCoordinatorFactory.createCoordinator(name, this)
    private data class TileStopped(val tile: DominoTile) : LifecycleEvent

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        println("QQQ starting $coordinator")
        coordinator.start()
    }

    override fun stop() {
        println("QQQ stopping $coordinator")
        coordinator.stop()
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                println("QQQ started!")
            }
            is StopEvent -> {
                println("QQQ stoped!")
            }
            is ErrorEvent -> {
                println("QQQ error $event!")
            }
            is TimerEvent -> {
                println("QQQ timer $event!")
            }
            else -> {
                println("QQQ oops $event!")
            }
        }
    }

    fun stopped(dominoTile: DominoTile) {
        coordinator.postEvent(TileStopped(dominoTile))
    }
}
