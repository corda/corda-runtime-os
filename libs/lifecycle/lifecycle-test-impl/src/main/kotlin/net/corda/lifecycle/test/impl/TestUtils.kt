package net.corda.lifecycle.test.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.impl.registry.LifecycleRegistryCoordinatorAccess

fun LifecycleCoordinator.isUp(): Boolean {
    return this.status == LifecycleStatus.UP
}

fun LifecycleCoordinator.bringUp() {
    this.start()
    this.updateStatus(LifecycleStatus.UP)
}

fun LifecycleCoordinator.bringDown() {
    this.updateStatus(LifecycleStatus.DOWN)
}

inline fun <reified T> LifecycleRegistryCoordinatorAccess.getCoordinator(): LifecycleCoordinator {
    return this.getCoordinator(LifecycleCoordinatorName.forComponent<T>())
}
