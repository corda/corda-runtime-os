package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.CustomEvent
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
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
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Suppress("TooManyFunctions")
class DominoTile(
    componentName: String,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val createResources: ((resources: ResourcesHolder) -> CompletableFuture<Unit>)? = null,
    private val children: Collection<DominoTile> = emptySet(),
    private val configurationChangeHandler: ConfigurationChangeHandler<*>? = null,
) : Lifecycle {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    private object StartTile : LifecycleEvent
    private data class StopTile(val dueToError: Boolean) : LifecycleEvent
    private data class ConfigApplied(val configUpdateResult: ConfigUpdateResult) : LifecycleEvent
    private data class NewConfig(val config: Config) : LifecycleEvent
    private object ResourcesCreated : LifecycleEvent
    enum class State {
        Created,
        Started,
        StoppedDueToError,
        StoppedDueToBadConfig,
        StoppedByParent
    }
    class StatusChangeEvent(val oldState: State, val newState: State): LifecycleEvent

    private val dominoTileName = LifecycleCoordinatorName(
        componentName,
        instancesIndex.compute(componentName) { _, last ->
            if (last == null) {
                1
            } else {
                last + 1
            }
        }.toString()
    )
    val name: LifecycleCoordinatorName
        get() = dominoTileName

    private val logger = LoggerFactory.getLogger(name.toString())

    private val controlLock = ReentrantReadWriteLock()

    override fun start() {
        coordinator.start()
        coordinator.postEvent(StartTile)
    }

    override fun stop() {
        coordinator.postEvent(StopTile(false))
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

    private val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())
    private val resources = ResourcesHolder()
    private val configResources = ResourcesHolder()

    @Volatile
    private var registrations: MutableMap<RegistrationHandle, Pair<DominoTile, State>> = children.map {
        val dominoTileName = it.name
        coordinator.followStatusChangesByName(setOf(dominoTileName)) to Pair(it, it.state)
    }.toMap().toMutableMap()

    private val currentState = AtomicReference(State.Created)

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
            gotError(error)
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

    val state: State
        get() = currentState.get()

    override val isRunning: Boolean
        get() = state == State.Started

    private fun newConfig(config: Config) {
        coordinator.postEvent(NewConfig(config))
    }

    private fun updateState(newState: State) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                State.Started -> LifecycleStatus.UP
                State.StoppedByParent, State.StoppedDueToBadConfig -> LifecycleStatus.DOWN
                State.StoppedDueToError -> LifecycleStatus.ERROR
                State.Created -> null
            }
            withLifecycleWriteLock {
                status?.let { coordinator.updateStatus(it) }
                coordinator.postCustomEventToFollowers(StatusChangeEvent(oldState, newState))
            }
            logger.info("State of $name updated from $oldState to $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        private fun handleControlEvent(event: LifecycleEvent) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent, is StopEvent -> {
                    // We don't do anything when starting/stopping the coordinator
                }
                is StopTile -> {
                    if (event.dueToError) {
                        when(state) {
                            State.StoppedByParent, State.StoppedDueToBadConfig, State.StoppedDueToError -> {}
                            State.Started, State.Created -> {
                                stopTile()
                                updateState(State.StoppedDueToError)
                            }
                        }
                    } else {
                        when(state) {
                            State.StoppedByParent, State.StoppedDueToBadConfig, State.StoppedDueToError -> {}
                            State.Started, State.Created -> {
                                stopTile()
                                updateState(State.StoppedByParent)
                            }
                        }
                    }
                }
                is StartTile -> {
                    when (state) {
                        State.Created, State.StoppedByParent -> startDependenciesIfNeeded()
                        State.Started -> {} // Do nothing
                        State.StoppedDueToError -> logger.warn("Can not start $name, it was stopped due to an error")
                        State.StoppedDueToBadConfig -> logger.warn("Can not start $name, it was stopped due to bad config")
                    }
                }
                is RegistrationStatusChangeEvent -> {
                    // we don't react to UP/DOWN signals, since we have our custom events that indicate every status change.
                }
                is CustomEvent -> {
                    if (event.payload is StatusChangeEvent) {
                        val statusChangeEvent = event.payload as StatusChangeEvent

                        val childWithState = registrations[event.registration]
                        if (childWithState == null) {
                            logger.warn("Signal change status received from registration " +
                                    "(${event.registration}) that didn't map to a component.")
                            return
                        }
                        registrations[event.registration] = childWithState.copy(second = statusChangeEvent.newState)
                        val (child, _) = childWithState

                        when(statusChangeEvent.newState) {
                            State.Started -> {
                                logger.info("Status change: child ${child.name} went up.")
                                handleLifecycleUp(child)
                            }
                            State.StoppedDueToBadConfig, State.StoppedDueToError -> {
                                logger.info("Status change: child ${child.name} went down (${statusChangeEvent.newState}).")
                                coordinator.postEvent(StopTile(true))
                            }
                            State.Created, State.StoppedByParent -> {  }
                        }
                    }
                }
                is ResourcesCreated -> {
                    resourcesReady = true
                    when (state) {
                        State.StoppedDueToBadConfig, State.Created, State.StoppedByParent -> {
                            logger.info("Resources ready for $name.")
                            setStartedIfCan()
                        }
                        State.StoppedDueToError, State.Started -> {} // Do nothing
                    }
                }
                is ConfigApplied -> {
                    when (event.configUpdateResult) {
                        ConfigUpdateResult.Success -> {
                            configReady = true
                            when (state) {
                                State.StoppedDueToBadConfig -> {
                                    logger.info("Config applied successfully for $name.")
                                    startDependenciesIfNeeded()
                                }
                                State.Created, State.StoppedByParent -> {
                                    logger.info("Config applied successfully for $name.")
                                    setStartedIfCan()
                                }
                                State.StoppedDueToError, State.Started -> {} // Do nothing
                            }
                        }
                        is ConfigUpdateResult.Error -> {
                            logger.warn("Config error for $name. ${event.configUpdateResult.e}")
                            stopTile(false)
                            updateState(State.StoppedDueToBadConfig)
                        }
                        ConfigUpdateResult.NoUpdate -> {
                            logger.info("Config applied with no update for $name.")
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
        logger.info("Got new configuration for $name")
        if (newConfiguration == configurationChangeHandler.lastConfiguration) {
            logger.info("Configuration same with previous for $name, so not applying it.")
            configApplied(ConfigUpdateResult.NoUpdate)
        } else {
            logger.info("Applying new configuration for $name")
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

    private fun gotError(cause: Throwable) {
        logger.warn("Got error in $name", cause)
        coordinator.postEvent(StopTile(true))
    }

    private fun handleLifecycleUp(child: DominoTile) {
        if (!isRunning) {
            val childrenWithStatus = registrations.values
            when {
                childrenWithStatus.all { it.second == State.Started } -> {
                    logger.info("Starting resources, since all children are now up.")
                    createResourcesAndStart()
                }
                childrenWithStatus.any { it.second == State.StoppedDueToError || it.second == State.StoppedDueToBadConfig } -> {
                    logger.info("Stopping child ${child.name} that went up, since there are other children that are in errored state.")
                    child.stop()
                }
                // any children that are not running have been stopped by the parent.
                childrenWithStatus.all { it.second == State.Started || it.second == State.StoppedByParent } -> {
                    logger.info("Starting other children that had been stopped by me.")
                    startDependenciesIfNeeded()
                }
                else -> {}
            }
        }
    }

    private fun startDependenciesIfNeeded() {
        registrations.values.map { it.first }.forEach {
            logger.info("Starting child ${it.name}")
            it.start()
        }
        if (registrations.all { it.value.second == State.Started }) {
            @Suppress("TooGenericExceptionCaught")
            try {
                createResourcesAndStart()
            } catch (e: Throwable) {
                gotError(e)
            }
        } else {
            logger.info(
                "Not all child tiles started yet.\n " +
                    "Started Children = ${registrations.filter{ it.value.second == State.Started }
                        .map { "(${it.value.first.name}, ${it.value.second})" }.joinToString()}.\n " +
                    "Not Started Children = ${registrations.filter { it.value.second != State.Started }
                        .map { "(${it.value.first.name}, ${it.value.second})" }.joinToString()}."
            )
        }
    }

    private fun shouldNotWaitForConfig(): Boolean {
        return configurationChangeHandler == null || configReady
    }

    private fun shouldNotWaitForResource(): Boolean {
        return createResources == null || resourcesReady
    }

    private fun createResourcesAndStart() {
        if (createResources != null) {
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
            logger.info("Registering for Config Updates $name.")
            configRegistration =
                configurationChangeHandler.configurationReaderService
                    .registerForUpdates(
                        Handler(configurationChangeHandler)
                    )
        }
        setStartedIfCan()
    }

    private fun setStartedIfCan() {
        if (shouldNotWaitForResource() && shouldNotWaitForConfig()) {
            updateState(State.Started)
        }
    }

    private fun stopTile(stopConfigListener: Boolean = true) {
        logger.info("Stopping resources")
        resources.close()
        resourcesReady = false
        configResources.close()

        if (stopConfigListener) {
            stopListeningForConfig()
        }
        configReady = false

        registrations.values.forEach {
            if (!(it.second == State.StoppedDueToError || it.second == State.StoppedDueToBadConfig )) {
                logger.info("Stopping child ${it.first.name}")
                it.first.stop()
            }
        }
    }

    private fun stopListeningForConfig() {
        configRegistration?.close()
        if (configRegistration != null) logger.info("Unregistered for Config Updates $name.")
        configurationChangeHandler?.lastConfiguration = null
        configRegistration = null
        configurationChangeHandler?.lastConfiguration = null
    }

    override fun close() {
        registrations.forEach {
            it.key.close()
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
        registrations.values.forEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.first.close()
            } catch (e: Throwable) {
                logger.warn("Could not close ${it.first.name}", e)
            }
        }
    }

    override fun toString(): String {
        return "$name (state: $state, children: $children)"
    }
}
