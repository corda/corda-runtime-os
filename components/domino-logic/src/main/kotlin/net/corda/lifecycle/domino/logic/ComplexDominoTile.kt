package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.DominoTileState.Created
import net.corda.lifecycle.domino.logic.DominoTileState.Started
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedByParent
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToBadConfig
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToChildStopped
import net.corda.lifecycle.domino.logic.DominoTileState.StoppedDueToError
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * This class encapsulates more complicated domino logic for components that might need to start external resources,
 * react to configuration changes and depend on / manage other components.
 *
 * The order of events is the following:
 * - once [start] is invoked, the [managedChildren] will be [start]ed, if any.
 * - then it will wait for [dependentChildren] to start.
 * - once they have started, [createResources] will be invoked, if specified.
 * - once resources are created and configuration has been applied successfully, the component will be fully started.
 *
 * @param createResources the callback method used to start any resources needed by the tile.
 *  When the tile stops, these resources will be stopped. If there are no resources, it can be left undefined.
 * @param managedChildren the children tiles this tile is responsible for starting when it starts.
 * @param dependentChildren the children tiles this component requires in order to function properly.
 *  If one of them goes down, this tile will also go down.
 * @param configurationChangeHandler the callback handler that handles new configuration.
 * If no configuration is needed, it can be left undefined.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class ComplexDominoTile(
    componentName: String,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)? = null,
    override val dependentChildren: Collection<DominoTile> = emptySet(),
    override val managedChildren: Collection<DominoTile> = emptySet(),
    private val configurationChangeHandler: ConfigurationChangeHandler<*>? = null,
) : DominoTile {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    private object StartTile : LifecycleEvent
    private object StopTile : LifecycleEvent
    private data class ConfigApplied(val configUpdateResult: ConfigUpdateResult) : LifecycleEvent
    private data class NewConfig(val config: Config) : LifecycleEvent
    private object ResourcesCreated : LifecycleEvent

    override val coordinatorName: LifecycleCoordinatorName by lazy {
        LifecycleCoordinatorName(
            componentName,
            instancesIndex.compute(componentName) { _, last ->
                if (last == null) {
                    1
                } else {
                    last + 1
                }
            }.toString()
        )
    }

    private val logger = LoggerFactory.getLogger(coordinatorName.toString())

    private val controlLock = ReentrantReadWriteLock()

    override fun start() {
        coordinator.start()
        coordinator.postEvent(StartTile)
    }

    override fun stop() {
        coordinator.postEvent(StopTile)
    }

    fun <T> withLifecycleLock(access: () -> T): T {
        return controlLock.read {
            access.invoke()
        }
    }

    fun <T> withLifecycleWriteLock(access: () -> T): T {
        return controlLock.write {
            access.invoke()
        }
    }

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())
    private val resources = ResourcesHolder()
    private val configResources = ResourcesHolder()

    private val registrationToChildMap: Map<RegistrationHandle, DominoTile> = dependentChildren.associateBy {
        coordinator.followStatusChangesByName(setOf(it.coordinatorName))
    }
    private val latestChildStateMap = dependentChildren.associateWith {
        it.state
    }.toMutableMap()

    private val currentState = AtomicReference(Created)

    private val isOpen = AtomicBoolean(true)

    @Volatile
    private var configReady = false
    @Volatile
    private var resourcesReady = false
    @Volatile
    private var configRegistration: AutoCloseable? = null

    private sealed class ConfigUpdateResult {
        object Success : ConfigUpdateResult()
        object NoUpdate : ConfigUpdateResult()
        data class Error(val e: Throwable) : ConfigUpdateResult()
    }

    private fun configApplied(configUpdateResult: ConfigUpdateResult) {
        coordinator.postEvent(ConfigApplied(configUpdateResult))
    }

    private fun resourcesStarted(error: Throwable? = null) {
        if (error != null) {
            coordinator.postEvent(ErrorEvent(error))
        } else {
            coordinator.postEvent(ResourcesCreated)
        }
    }

    private inner class Handler(private val configurationChangeHandler: ConfigurationChangeHandler<*>) : ConfigurationHandler {
        override fun onNewConfiguration(changedKeys: Set<String>, config: Map<String, SmartConfig>) {
            if (changedKeys.contains(configurationChangeHandler.key)) {
                val newConfiguration = config[configurationChangeHandler.key]
                if (newConfiguration != null) {
                    newConfig(newConfiguration)
                }
            }
        }
    }

    override val state: DominoTileState
        get() = currentState.get()

    override val isRunning: Boolean
        get() = state == Started

    private fun newConfig(config: Config) {
        coordinator.postEvent(NewConfig(config))
    }

    private fun updateState(newState: DominoTileState) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                Started -> LifecycleStatus.UP
                StoppedDueToBadConfig, StoppedByParent, StoppedDueToChildStopped -> LifecycleStatus.DOWN
                StoppedDueToError -> LifecycleStatus.ERROR
                Created -> null
            }
            withLifecycleWriteLock {
                status?.let { coordinator.updateStatus(it) }
                coordinator.postCustomEventToFollowers(StatusChangeEvent(newState))
            }
            logger.info("State updated from $oldState to $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        private fun handleControlEvent(event: LifecycleEvent) {
            when (event) {
                is ErrorEvent -> {
                    stopResources()
                    stopListeningForConfig()
                    updateState(StoppedDueToError)
                }
                is StartEvent -> {
                    // The coordinator had started, set the children state map - from
                    // now on we should receive messages of any change
                    latestChildStateMap += dependentChildren.associateWith {
                        it.state
                    }
                }
                is StopEvent -> {
                    // We don't do anything when stopping the coordinator
                }
                is StopTile -> {
                    if (state != StoppedByParent) {
                        stopTile()
                        updateState(StoppedByParent)
                    }
                }
                is StartTile -> {
                    when (state) {
                        Created, StoppedByParent -> startDependenciesIfNeeded()
                        Started, StoppedDueToChildStopped -> {} // Do nothing
                        StoppedDueToError -> logger.warn("Can not start, since currently being stopped due to an error")
                        StoppedDueToBadConfig -> logger.warn("Can not start, since currently being stopped due to bad config")
                    }
                }
                is RegistrationStatusChangeEvent -> {
                    // we don't react to UP/DOWN signals, since we have our custom events that indicate every status change.
                }
                is CustomEvent -> {
                    if (event.payload is StatusChangeEvent) {
                        val statusChangeEvent = event.payload as StatusChangeEvent

                        val child = registrationToChildMap[event.registration]
                        if (child == null) {
                            logger.warn(
                                "Signal change status received from registration " +
                                        "(${event.registration}) that didn't map to a component."
                            )
                            return
                        }
                        latestChildStateMap[child] = statusChangeEvent.newState

                        when (statusChangeEvent.newState) {
                            Started -> {
                                logger.info("Status change: child ${child.coordinatorName} went up.")
                                handleChildStarted()
                            }
                            StoppedDueToBadConfig, StoppedDueToError, StoppedByParent, StoppedDueToChildStopped -> {
                                logger.info("Status change: child ${child.coordinatorName} went down (${statusChangeEvent.newState}).")
                                handleChildDown()
                            }
                            Created -> { }
                        }
                    }
                }
                is ResourcesCreated -> {
                    resourcesReady = true
                    setStartedIfCan()
                }
                is ConfigApplied -> {
                    when (event.configUpdateResult) {
                        ConfigUpdateResult.Success -> {
                            if (state == StoppedDueToBadConfig) {
                                logger.info(
                                    "Received valid config for $coordinatorName, which was previously stopped due to invalid config."
                                )
                            } else {
                                logger.info("Received valid config for $coordinatorName.")
                            }
                            configReady = true
                            createResourcesAndStart()
                        }
                        is ConfigUpdateResult.Error -> {
                            logger.warn("Config error ${event.configUpdateResult.e}")
                            stopResources()
                            updateState(StoppedDueToBadConfig)
                        }
                        ConfigUpdateResult.NoUpdate -> {
                            logger.info("Config applied with no update.")
                        }
                    }
                }
                is NewConfig -> {
                    configurationChangeHandler?.let { handleConfigChange(it, event.config) }
                }
                else -> logger.warn("Unexpected event $event")
            }
        }

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            if (!isOpen.get()) {
                return
            }

            withLifecycleWriteLock {
                handleControlEvent(event)
            }
        }
    }

    private fun <C> handleConfigChange(configurationChangeHandler: ConfigurationChangeHandler<C>, config: Config) {
        val newConfiguration = try {
            configurationChangeHandler.configFactory(config)
        } catch (e: Exception) {
            configApplied(ConfigUpdateResult.Error(e))
            return
        }
        logger.info("Got new configuration")
        if (newConfiguration == configurationChangeHandler.lastConfiguration) {
            configApplied(ConfigUpdateResult.NoUpdate)
        } else {
            logger.info("Applying new configuration")
            val future = configurationChangeHandler.applyNewConfiguration(
                newConfiguration,
                configurationChangeHandler.lastConfiguration,
                configResources
            )
            future.whenComplete { _, exception ->
                if (exception != null) {
                    configApplied(ConfigUpdateResult.Error(exception))
                } else {
                    configApplied(ConfigUpdateResult.Success)
                }
            }
            configurationChangeHandler.lastConfiguration = newConfiguration
        }
    }

    private fun handleChildStarted() {
        if (!isRunning) {
            if (dependentChildren.all { latestChildStateMap[it] == Started }) {
                logger.info("Starting resources, since all children are now up.")
                createResourcesAndStart()
            }
        }
    }

    private fun handleChildDown() {
        stopResources()
        stopListeningForConfig()
        updateState(StoppedDueToChildStopped)
    }

    private fun startDependenciesIfNeeded() {
        managedChildren.forEach {
            logger.info("Starting child ${it.coordinatorName}")
            it.start()
        }

        // if there are dependent children, we wait for them before starting resources. Otherwise, we can start them immediately.
        if (dependentChildren.isEmpty()) {
            @Suppress("TooGenericExceptionCaught")
            try {
                createResourcesAndStart()
            } catch (e: Throwable) {
                coordinator.postEvent(ErrorEvent(e))
            }
        }
    }

    private fun shouldNotWaitForConfig(): Boolean {
        return configurationChangeHandler == null || configReady
    }

    private fun shouldNotWaitForResource(): Boolean {
        return createResources == null || resourcesReady
    }

    private fun shouldNotWaitForChildren(): Boolean {
        return dependentChildren.all { latestChildStateMap[it] == Started }
    }

    private fun createResourcesAndStart() {
        if (createResources != null && !resourcesReady) {
            resources.close()
            logger.info("Starting resources")
            val future = createResources.invoke(resources)
            future.whenComplete { _, exception ->
                if (exception != null) {
                    resourcesStarted(exception)
                } else {
                    resourcesStarted()
                }
            }
        }

        if (configRegistration == null && configurationChangeHandler != null) {
            logger.info("Registering for Config updates.")
            configRegistration =
                configurationChangeHandler.configurationReaderService
                    .registerForUpdates(
                        Handler(configurationChangeHandler)
                    )
        }
        setStartedIfCan()
    }

    private fun setStartedIfCan() {
        if (shouldNotWaitForResource() && shouldNotWaitForConfig() && shouldNotWaitForChildren()) {
            updateState(Started)
        }
    }

    private fun stopTile() {
        stopResources()
        stopListeningForConfig()
        stopChildren()
    }

    private fun stopResources() {
        logger.info("Stopping resources")
        resources.close()
        resourcesReady = false
        configResources.close()
    }

    private fun stopChildren() {
        managedChildren.forEach {
            logger.info("Stopping child ${it.coordinatorName}")
            it.stop()
        }
    }

    private fun stopListeningForConfig() {
        configRegistration?.close()
        if (configRegistration != null) logger.info("Unregistered for Config updates.")
        configurationChangeHandler?.lastConfiguration = null
        configRegistration = null
        configurationChangeHandler?.lastConfiguration = null
        configReady = false
    }

    override fun close() {
        registrationToChildMap.keys.forEach {
            it.close()
        }
        configRegistration?.close()
        configRegistration = null
        configurationChangeHandler?.lastConfiguration = null
        configResources.close()
        withLifecycleWriteLock {
            isOpen.set(false)

            stopTile()

            try {
                coordinator.close()
            } catch (e: LifecycleException) {
                // This try-catch should be removed once CORE-2786 is fixed
                logger.debug("Could not close coordinator", e)
            }
        }
        managedChildren.forEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.close()
            } catch (e: Throwable) {
                logger.warn("Could not close ${it.coordinatorName}", e)
            }
        }
    }

    override fun toString(): String {
        return "$coordinatorName (state: $state, dependent children: ${dependentChildren.map { it.coordinatorName }}, " +
                "managed children: ${managedChildren.map { it.coordinatorName }})"
    }
}
