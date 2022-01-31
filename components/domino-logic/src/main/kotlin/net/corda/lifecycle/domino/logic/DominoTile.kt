package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
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
import net.corda.v5.base.util.contextLogger
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
        private val logger = contextLogger()
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

    val name = LifecycleCoordinatorName(
        componentName,
        instancesIndex.compute(componentName) { _, last ->
            if (last == null) {
                1
            } else {
                last + 1
            }
        }.toString()
    )

    private val controlLock = ReentrantReadWriteLock()

    override fun start() {
        logger.info("Starting $name")
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
    private var registrations: Collection<RegistrationHandle>? = null

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
            }
            logger.info("State of $name is $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        private fun handleControlEvent(event: LifecycleEvent) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent, is StopEvent -> {
                    // We don't do anything when the starting/stopping the coordinator
                }
                is StopTile -> {
                    when (state) {
                        State.StoppedByParent, State.StoppedDueToBadConfig, State.StoppedDueToError -> {}
                        else -> {
                            stopTile()
                            if (event.dueToError) {
                                updateState(State.StoppedDueToError)
                            } else {
                                updateState(State.StoppedByParent)
                            }
                        }
                    }
                }
                is StartTile -> {
                    when (state) {
                        State.Created, State.StoppedByParent -> readyOrStartTile()
                        State.Started -> {} // Do nothing
                        State.StoppedDueToError -> logger.warn("Can not start $name, it was stopped due to an error")
                        State.StoppedDueToBadConfig -> logger.warn("Can not start $name, it was stopped due to bad config")
                    }
                }
                is RegistrationStatusChangeEvent -> {
                    if (event.status == LifecycleStatus.UP) {
                        logger.info("RegistrationStatusChangeEvent $name going up.")
                        handleLifecycleUp()
                    } else {
                        val errorKids = children.filter { it.state == State.StoppedDueToError || it.state == State.StoppedDueToBadConfig }
                        if (errorKids.isEmpty()) {
                            stop()
                        } else {
                            gotError(Exception("$name Had error in ${errorKids.map { it.name }}"))
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
                                    logger.info("Received valid config for $name, this was previously invalid.")
                                    startDependenciesIfNeeded()
                                }
                                State.Created, State.StoppedByParent -> {
                                    logger.info("Received valid config for $name.")
                                    setStartedIfCan()
                                }
                                State.StoppedDueToError, State.Started -> {
                                    logger.info("Received valid config for $name.")
                                } // Do nothing
                            }
                        }
                        is ConfigUpdateResult.Error -> {
                            logger.warn("Config error for $name. ${event.configUpdateResult.e}")
                            stopTile(false)
                            updateState(State.StoppedDueToBadConfig)
                        }
                        ConfigUpdateResult.NoUpdate -> {
                            logger.info("No Config update for $name.")
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
            logger.debug("Exception when $name processed config change posting this to the coordinator.")
            configApplied(ConfigUpdateResult.Error(e))
            return
        }
        if (newConfiguration == configurationChangeHandler.lastConfiguration) {
            logger.debug("Configuration change did not update $name posting this to the coordinator.")
            configApplied(ConfigUpdateResult.NoUpdate)
        } else {
            val future = configurationChangeHandler.applyNewConfiguration(
                newConfiguration,
                configurationChangeHandler.lastConfiguration,
                configResources
            )
            future.whenComplete { _, exception ->
                if (exception != null) {
                    logger.debug("Asynchronous exception when $name processed config change posting this to the coordinator.")
                    configApplied(ConfigUpdateResult.Error(exception))
                } else {
                    logger.debug("$name processed config change successfully posting this to the coordinator.")
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

    private fun handleLifecycleUp() {
        if (!isRunning) {
            when {
                children.all { it.isRunning } -> {
                    createResourcesAndStart()
                }
                children.any { it.state == State.StoppedDueToError || it.state == State.StoppedDueToBadConfig } -> {
                    children.filter { it.isRunning || it.state == State.Created }.forEach { it.stop() }
                }
                else -> { // any children that are not running have been stopped by the parent
                    startDependenciesIfNeeded()
                }
            }
        }
    }

    private fun readyOrStartTile() {
        if (registrations == null && children.isNotEmpty()) {
            registrations = children.map {
                it.name
            }.map {
                coordinator.followStatusChangesByName(setOf(it))
            }
            logger.info("Created $name with ${children.map { it.name }}")
        }
        startDependenciesIfNeeded()
    }

    private fun startDependenciesIfNeeded() {
        children.forEach {
            it.start()
        }
        if (children.all { it.isRunning }) {
            @Suppress("TooGenericExceptionCaught")
            try {
                createResourcesAndStart()
            } catch (e: Throwable) {
                gotError(e)
            }
        } else {
            logger.info(
                "Not all child tiles started yet.\n " +
                    "Started Children = ${children.filter{ it.isRunning }}.\n " +
                    "Not Started Children = ${children.filter { !it.isRunning }}."
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
        resources.close()

        val future = createResources?.invoke(resources)
        future?.whenComplete { _, exception ->
            if (exception != null) {
                resourcesStarted(exception)
            } else {
                resourcesStarted()
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
        resources.close()
        resourcesReady = false
        configResources.close()

        if (stopConfigListener) {
            configRegistration?.close()
            if (configRegistration != null) logger.info("Unregistered for Config Updates $name.")
            configurationChangeHandler?.lastConfiguration = null
            configRegistration = null
            configurationChangeHandler?.lastConfiguration = null
        }
        configReady = false

        children.forEach {
            if (!(it.state == State.StoppedDueToError || it.state == State.StoppedDueToBadConfig)) {
                it.stop()
            }
        }
    }

    override fun close() {
        registrations?.forEach {
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
        children.reversed().forEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.close()
            } catch (e: Throwable) {
                logger.warn("Could not close $it", e)
            }
        }
    }

    override fun toString(): String {
        return "$name: $state: $children"
    }
}
