package net.corda.securitymanager.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.createCoordinator
import net.corda.schema.configuration.ConfigKeys.SECURITY_CONFIG
import net.corda.schema.configuration.ConfigKeys.SECURITY_POLICY
import net.corda.securitymanager.SecurityConfigHandler
import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.io.InputStream

/** An implementation of [SecurityConfigHandler]. */
@Suppress("unused")
@Component(immediate = true, service = [SecurityConfigHandler::class])
class SecurityConfigHandlerImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SecurityManagerService::class)
    private val securityManagerService: SecurityManagerService,
): SecurityConfigHandler {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val coordinator = coordinatorFactory.createCoordinator<SecurityConfigHandler>(::eventHandler)
    private val configReadServiceHandle: AutoCloseable
    private var configHandle: AutoCloseable? = null

    init {
        log.debug("Initializing SecurityConfigHandler")
        configReadServiceHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
        start()
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
        log.info("SecurityConfigHandler started")
    }

    override fun stop() {
        coordinator.stop()
    }

    @Deactivate
    fun close() {
        configReadServiceHandle.close()
        coordinator.close()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "SecurityConfigHandler received: $event" }
        when (event) {
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerComponentForUpdates(
                        coordinator,
                        setOf(SECURITY_CONFIG)
                    )
                    coordinator.updateStatus(LifecycleStatus.UP)
                } else {
                    configHandle?.close()
                    coordinator.updateStatus(LifecycleStatus.DOWN)
                }
            }
            is ConfigChangedEvent -> {
                event.config[SECURITY_CONFIG]?.let {
                    applyPolicy(it)
                }
            }
        }
    }

    /**
     * Applies policy from [securityConfig]
     */
    private fun applyPolicy(securityConfig: SmartConfig) {
        if (securityConfig.hasPath(SECURITY_POLICY)) {
            try {
                log.info("Applying security policy from configuration")
                securityConfig.getString(SECURITY_POLICY).byteInputStream().use(::updatePermissions)
            } catch (e: Exception) {
                log.error("Error applying security policy from configuration: ${e.message}", e)
            }
        } else {
            log.debug { "No configuration value found for key '$SECURITY_POLICY'" }
        }
    }

    /**
     * Reads permissions from [inputStream] and adds them to the start of permissions list.
     *
     * If [clear] is set, the existing permissions are cleared first.
     */
    private fun updatePermissions(inputStream: InputStream) {
        val permissions = securityManagerService.readPolicy(inputStream)
        securityManagerService.updatePermissions(permissions, clear = true)
    }
}
