package net.corda.lifecycle.test.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.mock

class LifecycleTest<T : Lifecycle>(
    initializer: LifecycleTest<T>.() -> Unit = {}
) {

    private val dependencies: MutableSet<LifecycleCoordinatorName> = mutableSetOf()
    val coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()

    private val registry
        get() = coordinatorFactory.registry

    inline fun <reified T> addDependency(): LifecycleCoordinator {
        return addDependency(LifecycleCoordinatorName.forComponent<T>())
    }

    fun addDependency(name: LifecycleCoordinatorName): LifecycleCoordinator {
        dependencies.add(name)
        return coordinatorFactory.createCoordinator(name, mock())
    }

    fun verifyIsUp(coordinatorName: LifecycleCoordinatorName) {
        assertThat(registry.getCoordinator(coordinatorName).status).isEqualTo(LifecycleStatus.UP)
    }

    inline fun <reified T> verifyIsUp() {
        verifyIsUp(LifecycleCoordinatorName.forComponent<T>())
    }

    fun verifyIsDown(coordinatorName: LifecycleCoordinatorName) {
        assertThat(registry.getCoordinator(coordinatorName).status)
            .withFailMessage("$coordinatorName was not DOWN")
            .isEqualTo(LifecycleStatus.DOWN)
    }

    inline fun <reified T> verifyIsDown() {
        verifyIsDown(LifecycleCoordinatorName.forComponent<T>())
    }

    fun bringDependenciesUp() {
        dependencies.forEach {
            registry.getCoordinator(it).bringUp()
        }
    }

    fun bringDependencyUp(coordinatorName: LifecycleCoordinatorName) {
        val coordinator = registry.getCoordinator(coordinatorName)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    inline fun <reified T> bringDependencyUp() {
        bringDependencyUp(LifecycleCoordinatorName.forComponent<T>())
    }

    fun bringDependenciesDown() {
        dependencies.forEach {
            registry.getCoordinator(it).bringDown()
        }
    }

    fun bringDependencyDown(coordinatorName: LifecycleCoordinatorName) {
        val coordinator = registry.getCoordinator(coordinatorName)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    inline fun <reified T> bringDependencyDown() {
        bringDependencyDown(LifecycleCoordinatorName.forComponent<T>())
    }

    inline fun <reified T> toggleDependency(
        verificationWhenDown: () -> Unit = {},
        verificationWhenUp: () -> Unit = {},
    ) {
        bringDependencyDown<T>()
        verifyIsDown<T>()
        verificationWhenDown.invoke()
        bringDependencyUp<T>()
        verifyIsUp<T>()
        verificationWhenUp.invoke()
    }

    private fun LifecycleCoordinator.bringUp() {
        this.start()
        this.updateStatus(LifecycleStatus.UP)
    }

    private fun LifecycleCoordinator.bringDown() {
        this.updateStatus(LifecycleStatus.DOWN)
    }

    init {
        initializer.invoke(this)
    }
}
