package net.corda.lifecycle

interface LifecycleCoordinatorSchedulerFactory {

    fun create(): LifecycleCoordinatorScheduler
}