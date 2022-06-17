package net.corda.lifecycle.domino.logic

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.libs.configuration.SmartConfig
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
 * - once they have started, [onStart] will be invoked, if specified.
 * - once resources are created and configuration has been applied successfully, the component will be fully started.
 *
 * @param onStart the callback method used to signal some external component when the tile starts.
 * @param onClose the callback method used to signal some external component when the tile is closed.
 * @param managedChildren the children this tile is responsible for starting when it starts.
 * @param dependentChildren the children this component requires in order to function properly.
 *  If one of them goes down, this tile will also go down.
 * @param configurationChangeHandler the callback handler that handles new configuration.
 * If no configuration is needed, it can be left undefined.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class ComplexDominoTile(
    componentName: String,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val onStart: (() -> Unit)? = null,
    private val onClose: (() -> Unit)? = null,
    override val dependentChildren: Collection<LifecycleCoordinatorName> = emptySet(),
    override val managedChildren: Collection<LifecycleWithCoordinatorName> = emptySet(),
    private val configurationChangeHandler: ConfigurationChangeHandler<*>? = null,
) : DominoTile() {

    companion object {
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    private data class ConfigApplied(val configUpdateResult: ConfigUpdateResult) : LifecycleEvent
    private data class NewConfig(val config: Config) : LifecycleEvent

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

    override fun stop() {
        coordinator.stop()
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

    override val coordinator = coordinatorFactory.createCoordinator(coordinatorName, EventHandler())
    private val configResources = ResourcesHolder()

    private val registrationToChildMap: Map<RegistrationHandle, LifecycleCoordinatorName> = dependentChildren.associateBy {
        coordinator.followStatusChangesByName(setOf(it))
    }
    private val latestChildStateMap = dependentChildren.associateWith {
        LifecycleStatus.DOWN
    }.toMutableMap()

    private val currentInternalState = AtomicReference(Created)

    private val internalState: DominoTileState
        get() = currentInternalState.get()

    private val isOpen = AtomicBoolean(true)

    @Volatile
    private var configReady = false
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

    private fun newConfig(config: Config) {
        coordinator.postEvent(NewConfig(config))
    }

    private fun updateState(newState: DominoTileState) {
        val oldState = currentInternalState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                Started -> LifecycleStatus.UP
                StoppedDueToBadConfig, StoppedByParent, StoppedDueToChildStopped -> LifecycleStatus.DOWN
                StoppedDueToError -> LifecycleStatus.ERROR
                Created -> null
            }
            withLifecycleWriteLock {
                status?.let { coordinator.updateStatus(it) }
            }
            logger.info("State updated from $oldState to $newState")
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        private fun handleControlEvent(event: LifecycleEvent) {
            when (event) {
                is ErrorEvent -> {
                    logger.error("An error occurred : ${event.cause.message}.", event.cause)
                    stopResources()
                    stopListeningForConfig()
                    updateState(StoppedDueToError)
                }
                is StartEvent -> {
                    when (internalState) {
                        Created, StoppedByParent -> startDependenciesIfNeeded()
                        Started, StoppedDueToChildStopped -> {} // Do nothing
                        StoppedDueToError -> logger.warn("Can not start, since currently being stopped due to an error")
                        StoppedDueToBadConfig -> logger.warn("Can not start, since currently being stopped due to bad config")
                    }
                }
                is StopEvent -> {
                    if (internalState != StoppedByParent) {
                        stopTile()
                        updateState(StoppedByParent)
                    }
                }
                is RegistrationStatusChangeEvent -> {
                    val child = registrationToChildMap[event.registration]
                    if (child == null) {
                        logger.warn("Registration status change event received from registration (${event.registration}) that didn't map" +
                            " to a component.")
                        return
                    }
                    latestChildStateMap[child] = event.status

                    when (event.status) {
                         LifecycleStatus.UP -> {
                            logger.info("Status change: child $child went up.")
                            handleChildStarted()
                        }
                        LifecycleStatus.DOWN -> {
                            logger.info("Status change: child $child went down.")
                            handleChildDownOrError()
                        }
                        LifecycleStatus.ERROR -> {
                            logger.info("Status change: child $child errored.")
                            handleChildDownOrError()
                        }
                    }
                }
                is ConfigApplied -> {
                    when (event.configUpdateResult) {
                        ConfigUpdateResult.Success -> {
                            if (currentInternalState.get() == StoppedDueToBadConfig) {
                                logger.info(
                                    "Received valid config for $coordinatorName, which was previously stopped due to invalid config."
                                )
                            } else {
                                logger.info("Received valid config for $coordinatorName.")
                            }
                            configReady = true
                            startIfDependantChildrenAndConfigReady()
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
            if (shouldNotWaitForChildren()) {
                logger.info("Starting resources, since all children are now up.")
                startIfDependantChildrenAndConfigReady()
            }
        }
    }

    private fun handleChildDownOrError() {
        stopResources()
        stopListeningForConfig()
        updateState(StoppedDueToChildStopped)
    }

    private fun startDependenciesIfNeeded() {
        managedChildren.forEach {
            logger.info("Starting child ${it.name}")
            it.lifecycle.start()
        }

        // if there are dependent children, we wait for them before starting resources. Otherwise, we can start them immediately.
        if (dependentChildren.isEmpty()) {
            @Suppress("TooGenericExceptionCaught")
            try {
                startIfDependantChildrenAndConfigReady()
            } catch (e: Throwable) {
                coordinator.postEvent(ErrorEvent(e))
            }
        }
    }

    private fun shouldNotWaitForConfig(): Boolean {
        return configurationChangeHandler == null || configReady
    }

    private fun shouldNotWaitForChildren(): Boolean {
        return dependentChildren.all { latestChildStateMap[it] == LifecycleStatus.UP }
    }

    private fun startIfDependantChildrenAndConfigReady() {
        if (configRegistration == null && configurationChangeHandler != null) {
            logger.info("Registering for Config updates.")
            configRegistration =
                configurationChangeHandler.configurationReaderService
                    .registerForUpdates(
                        Handler(configurationChangeHandler)
                    )
        }
        if (shouldNotWaitForConfig() && shouldNotWaitForChildren()) {
            onStart?.invoke()
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
        configResources.close()
    }

    private fun stopChildren() {
        managedChildren.forEach {
            logger.info("Stopping child ${it.name}")
            it.lifecycle.stop()
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
                it.lifecycle.close()
            } catch (e: Throwable) {
                logger.warn("Could not close ${it.name}", e)
            }
        }
        onClose?.invoke()
    }

    override fun toString(): String {
        return "$coordinatorName (state: ${coordinator.status}, dependent children: ${dependentChildren.map { it }}, " +
                "managed children: ${managedChildren.map { it.name }})"
    }
}
