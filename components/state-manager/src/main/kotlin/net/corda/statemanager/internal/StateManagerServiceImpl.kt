package net.corda.statemanager.internal

import java.util.function.Supplier
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.statemanager.StateManager
import net.corda.libs.statemanager.StateManagerFactory
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.statemanager.StateManagerService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [StateManagerService::class])
class StateManagerServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = StateManagerFactory::class)
    private val stateManagerFactory: StateManagerFactory,
) : StateManagerService, LifecycleEventHandler {

    override val stateManager: Supplier<StateManager?> get() = Supplier { _stateManager }
    private var _stateManager: StateManager? = null

    private val dependentComponents = DependentComponents.of(
        ::configReadService,
        ::dbConnectionManager,
    )
    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<StateManagerService>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, dependentComponents, this)
    private var registration: RegistrationHandle? = null
    private var configSubscription: AutoCloseable? = null

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event)
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
            is StopEvent -> onStopEvent()
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadService.start()
        registration?.close()
        registration =
            coordinator.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()))
    }

    private fun onStopEvent() {
        registration?.close()
        registration = null

        configSubscription?.close()
        configSubscription = null
    }

    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        val config = event.config[ConfigKeys.STATE_MANAGER_CONFIG] ?: return
        coordinator.updateStatus(LifecycleStatus.DOWN)

        _stateManager?.close()
        _stateManager = stateManagerFactory.create(config)

        coordinator.updateStatus(LifecycleStatus.UP)
    }

    private fun onRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        configSubscription = if (event.status == LifecycleStatus.UP) {
            configSubscription?.close()
            configReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.STATE_MANAGER_CONFIG))
        } else {
            coordinator.updateStatus(event.status)
            configSubscription?.close()
            null
        }
    }
}