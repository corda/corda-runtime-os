package net.corda.lifecycle

fun interface LifecycleEventHandler {

    fun processEvent(event: LifeCycleEvent, coordinator: LifeCycleCoordinator)
}