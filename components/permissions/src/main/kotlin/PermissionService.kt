
import net.corda.libs.permission.PermissionService
import net.corda.libs.permission.factory.PermissionServiceFactory
import net.corda.lifecycle.Lifecycle
import org.osgi.service.component.annotations.Reference

class PermissionService  (
        @Reference(service = PermissionServiceFactory::class)
        private val permissionServiceFactory: PermissionServiceFactory
) : Lifecycle {

    private var receivedSnapshot = false

    private var permissionService: PermissionService? = null

    override val isRunning: Boolean
        get() = receivedSnapshot
    
    override fun start() {
       permissionService?.stop()
        permissionService = permissionServiceFactory.createPermissionService()
    }

    override fun stop() {
      permissionService?.stop()
    }
}
