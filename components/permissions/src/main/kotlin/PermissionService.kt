import com.typesafe.config.Config
import net.corda.libs.permission.PermissionService
import net.corda.libs.permission.factory.PermissionServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

class PermissionService  (
        private val lifeCycleCoordinator: LifecycleCoordinator,
        @Reference(service = PermissionServiceFactory::class)
        private val permissionServiceFactory: PermissionServiceFactory
) : Lifecycle {

    companion object {
        private val log: Logger = contextLogger()
        const val MESSAGING_CONFIG: String = "corda.messaging"
    }

    private var receivedSnapshot = false

    private var permissionService: PermissionService? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: Config? = null

    override val isRunning: Boolean
        get() = receivedSnapshot

    fun start(bootstrapConfig: Config) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if(bootstrapConfig != null){
            permissionService = permissionServiceFactory.createPermissionService(bootstrapConfig)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
                if (!receivedSnapshot) {
                    if (changedKeys.contains(MESSAGING_CONFIG)) {
                        log.info("Config read service config snapshot received")
                        receivedSnapshot = true
                        lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
                    }
                } else {
                    log.info("Config read service config update received")
                    if (changedKeys.contains(MESSAGING_CONFIG)) {
                        log.info("Config update contains kafka config")
                        lifeCycleCoordinator.postEvent(MessagingConfigUpdateEvent(currentConfigurationSnapshot))
                    }
                }

            }
            sub = configReader!!.registerCallback(lister)
            configReader!!.start()
        } else {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            log.error(message)
            throw CordaRuntimeException(message)
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
