package net.corda.lifecycle.test.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.Resource
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap

class LifecycleTest<T : Lifecycle>(
    initializer: LifecycleTest<T>.() -> T
) {

    private val dependencies = ConcurrentHashMap.newKeySet<LifecycleCoordinatorName>()
    private val coordinatorToConfigKeys = mutableMapOf<LifecycleCoordinator, Set<String>>()
    private val configCoordinator = argumentCaptor<LifecycleCoordinator>()
    private val configKeys = argumentCaptor<Set<String>>()

    /**
     * This is the coordinator factory which the component under test can use to create its coordinator.
     *
     * This will replace the OSGi injected factory.
     */
    val coordinatorFactory = TestLifecycleCoordinatorFactoryImpl()

    /**
     * This mock can be used to verify the usage of the config handles by components.
     */
    var lastConfigHandle = mock<Resource>()

    /**
     * This mock will replace the OSGi injected config read service.
     */
    val configReadService = mock<ConfigurationReadService>().apply {
        whenever(registerComponentForUpdates(configCoordinator.capture(), configKeys.capture())).thenAnswer {
            coordinatorToConfigKeys[configCoordinator.lastValue] = configKeys.lastValue
            lastConfigHandle = mock()
            lastConfigHandle
        }
    }

    private val registry
        get() = coordinatorFactory.registry

    // Must go after all the initialization above as those classes may be used by the lambda
    /**
     * This is the actual instantiation of the component to be tested.  It is made available here
     * for use by test code later.
     */
    val testClass = initializer.invoke(this)

    /**
     * Registers the given class as a dependency of the component under test.  This allows for bringing
     * each dependency UP/DOWN as needed.
     */
    inline fun <reified D> addDependency(): LifecycleCoordinator {
        return addDependency(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Registers the given class as a dependency of the component under test.  This allows for bringing
     * each dependency UP/DOWN as needed.
     *
     * @param name the coordinator name of the dependency
     */
    fun addDependency(name: LifecycleCoordinatorName): LifecycleCoordinator {
        dependencies.add(name)
        return coordinatorFactory.createCoordinator(name, mock())
    }

    /**
     * Verify a component is in the [LifecycleStatus.UP] state
     *
     * @param coordinatorName the name of the coordinator to verify
     */
    fun verifyIsUp(coordinatorName: LifecycleCoordinatorName) {
        verifyIsStatus(coordinatorName, LifecycleStatus.UP)
    }

    /**
     * Verify a component is in the [LifecycleStatus.UP] state
     */
    inline fun <reified D> verifyIsUp() {
        verifyIsUp(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Verify a component is in the [LifecycleStatus.DOWN] state
     *
     * @param coordinatorName the name of the coordinator to verify
     */
    fun verifyIsDown(coordinatorName: LifecycleCoordinatorName) {
        verifyIsStatus(coordinatorName, LifecycleStatus.DOWN)
    }

    /**
     * Verify a component is in the [LifecycleStatus.DOWN] state
     */
    inline fun <reified D> verifyIsDown() {
        verifyIsDown(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Verify a component is in the [LifecycleStatus.ERROR] state
     *
     * @param coordinatorName the name of the coordinator to verify
     */
    fun verifyIsInError(coordinatorName: LifecycleCoordinatorName) {
        verifyIsStatus(coordinatorName, LifecycleStatus.ERROR)
    }

    /**
     * Verify a component is in the [LifecycleStatus.DOWN] state
     */
    inline fun <reified D> verifyIsInError() {
        verifyIsInError(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Bring all registered dependencies to the [LifecycleStatus.UP]
     */
    fun bringDependenciesUp() {
        dependencies.forEach {
            registry.getCoordinator(it).bringUp()
        }
    }

    /**
     * Change the given dependency to the [LifecycleStatus.UP] state.
     *
     * @param coordinatorName the name of the dependency to bring down
     */
    fun bringDependencyUp(coordinatorName: LifecycleCoordinatorName) {
        val coordinator = registry.getCoordinator(coordinatorName)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.UP)
    }

    /**
     * Change the given dependency to the [LifecycleStatus.UP] state.
     */
    inline fun <reified D> bringDependencyUp() {
        bringDependencyUp(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Bring all registered dependencies to the [LifecycleStatus.DOWN]
     */
    fun bringDependenciesDown() {
        dependencies.forEach {
            registry.getCoordinator(it).bringDown()
        }
    }

    /**
     * Change the given dependency to the [LifecycleStatus.DOWN] state.
     *
     * @param coordinatorName the name of the dependency to bring down
     */
    fun bringDependencyDown(coordinatorName: LifecycleCoordinatorName) {
        val coordinator = registry.getCoordinator(coordinatorName)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    /**
     * Change the given dependency to the [LifecycleStatus.DOWN] state.
     */
    inline fun <reified D> bringDependencyDown() {
        bringDependencyDown(LifecycleCoordinatorName.forComponent<D>())
    }

    /**
     * Change the given dependency to the [LifecycleStatus.ERROR] state.
     *
     * @param coordinatorName the name of the dependency to bring down
     */
    fun setDependencyToError(coordinatorName: LifecycleCoordinatorName) {
        val coordinator = registry.getCoordinator(coordinatorName)
        coordinator.start()
        coordinator.updateStatus(LifecycleStatus.ERROR)
    }

    /**
     * Change the given dependency to the [LifecycleStatus.ERROR] state.
     */
    inline fun <reified D> setDependencyToError() = setDependencyToError(LifecycleCoordinatorName.forComponent<D>())

    /**
     * Get the current coordinator by name
     */
    fun getCoordinatorFor(coordinatorName: LifecycleCoordinatorName): LifecycleCoordinator {
        return registry.getCoordinator(coordinatorName)
    }

    /**
     * Get the current coordinator by type
     */
    inline fun <reified D> getCoordinatorFor(): LifecycleCoordinator {
        return getCoordinatorFor(LifecycleCoordinatorName.forComponent<D>())
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

    /**
     * Send a configuration update to the component. This configuration update must _exactly_ match
     * what the coordinator for the class registers or an [IllegalArgumentException] will be thrown.
     *
     * @param config the new configuration update
     * @throws IllegalArgumentException if the keys in [config] don't match what the coordinator registered
     * for the component.
     */
    inline fun <reified D> sendConfigUpdate(
        config: Map<String, SmartConfig>
    ) {
        sendConfigUpdate(LifecycleCoordinatorName.forComponent<D>(), config)
    }

    /**
     * Send a configuration update to the component. This configuration update must _exactly_ match
     * what the coordinator for the class registers or an [IllegalArgumentException] will be thrown.
     *
     * @param coordinatorName the component's lifecycle coordinator name
     * @param config the new configuration update
     * @throws IllegalArgumentException if the keys in [config] don't match what the coordinator registered
     * for the component.
     */
    fun sendConfigUpdate(
        coordinatorName: LifecycleCoordinatorName,
        config: Map<String, SmartConfig>
    ) {

        val coordinator = registry.getCoordinator(coordinatorName)
        // assert that the config contains exactly the keys from required keys set
        // during registration
        val configKeys = coordinatorToConfigKeys[coordinator]
            ?: throw IllegalArgumentException("Keys must be registered with config read service for this test")
        if (configKeys != config.keys) {
            throw IllegalArgumentException("Test keys must match keys registered to the config read service for this test")
        }
        assertThat(configKeys).isEqualTo(config.keys)
        coordinator.postEvent(ConfigChangedEvent(configKeys, config))
    }

    private fun LifecycleCoordinator.bringUp() {
        this.start()
        this.updateStatus(LifecycleStatus.UP)
    }

    private fun LifecycleCoordinator.bringDown() {
        this.updateStatus(LifecycleStatus.DOWN)
    }

    private fun verifyIsStatus(coordinatorName: LifecycleCoordinatorName, expectedStatus: LifecycleStatus) {
        val status = registry.getCoordinator(coordinatorName).status
        assertThat(status)
            .withFailMessage("$coordinatorName was not in $expectedStatus.  Instead it was $status")
            .isEqualTo(expectedStatus)
    }
}
