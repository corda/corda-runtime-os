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

    inline fun <reified D> addDependency(): LifecycleCoordinator {
        return addDependency(LifecycleCoordinatorName.forComponent<D>())
    }

    fun addDependency(name: LifecycleCoordinatorName): LifecycleCoordinator {
        dependencies.add(name)
        return coordinatorFactory.createCoordinator(name, mock())
    }

    fun verifyIsUp(coordinatorName: LifecycleCoordinatorName) {
        assertThat(registry.getCoordinator(coordinatorName).status).isEqualTo(LifecycleStatus.UP)
    }

    inline fun <reified D> verifyIsUp() {
        verifyIsUp(LifecycleCoordinatorName.forComponent<D>())
    }

    fun verifyIsDown(coordinatorName: LifecycleCoordinatorName) {
        assertThat(registry.getCoordinator(coordinatorName).status)
            .withFailMessage("$coordinatorName was not DOWN")
            .isEqualTo(LifecycleStatus.DOWN)
    }

    inline fun <reified D> verifyIsDown() {
        verifyIsDown(LifecycleCoordinatorName.forComponent<D>())
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

    inline fun <reified D> bringDependencyUp() {
        bringDependencyUp(LifecycleCoordinatorName.forComponent<D>())
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

    inline fun <reified D> bringDependencyDown() {
        bringDependencyDown(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Bring down the given dependency and then bring it back up again.
     *
     * @param verificationWhenDown can be used to assert expectations for once the dependency is down
     * @param verificationWhenUp can be used to assert expectations for once the dependency is back up
     */
    inline fun <reified D> toggleDependency(
        noinline verificationWhenDown: () -> Unit = {},
        noinline verificationWhenUp: () -> Unit = {},
    ) = toggleDependency(
        LifecycleCoordinatorName.forComponent<D>(),
        verificationWhenDown,
        verificationWhenUp
    )

    /**
     * Bring down the given dependency and then bring it back up again.
     *
     * @param dependency the coordinator name of the dependency to toggle
     * @param verificationWhenDown can be used to assert expectations for once the dependency is down
     * @param verificationWhenUp can be used to assert expectations for once the dependency is back up
     */
    fun toggleDependency(
        dependency: LifecycleCoordinatorName,
        verificationWhenDown: () -> Unit = {},
        verificationWhenUp: () -> Unit = {},
    ) {
        bringDependencyDown(dependency)
        verifyIsDown(dependency)
        verificationWhenDown.invoke()
        bringDependencyUp(dependency)
        verifyIsUp(dependency)
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
