package net.corda.example.vnode

import java.io.FilePermission
import java.lang.management.ManagementPermission
import java.lang.reflect.ReflectPermission
import java.net.NetPermission
import java.net.SocketPermission
import java.nio.file.Files
import java.nio.file.LinkPermission
import java.nio.file.Paths
import java.util.PropertyPermission
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.sandboxes.SandboxSetup
import org.osgi.framework.AdminPermission
import org.osgi.framework.BundleContext
import org.osgi.framework.PackagePermission
import org.osgi.framework.ServicePermission
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.permissionadmin.PermissionAdmin

@Suppress("unused")
@Component(service = [], immediate = true)
class SandboxBootstrap @Activate constructor(
    @Reference
    securityManager: SecurityManagerService,
    @Reference
    sandboxSetup: SandboxSetup,
    bundleContext: BundleContext
){
    init {
        securityManager.start()
        securityManager.denyPermissions("FLOW/*", listOf(
            // OSGi permissions.
            AdminPermission(),
            ServicePermission("*", ServicePermission.GET),
            ServicePermission("net.corda.v5.*", ServicePermission.REGISTER),
            PackagePermission("net.corda", "${PackagePermission.EXPORTONLY},${PackagePermission.IMPORT}"),
            PackagePermission("net.corda.*", "${PackagePermission.EXPORTONLY},${PackagePermission.IMPORT}"),

            // Prevent the FLOW sandboxes from importing these packages,
            // which effectively forbids them from executing most OSGi code.
            PackagePermission("org.osgi.framework", PackagePermission.IMPORT),
            PackagePermission("org.osgi.service.component", PackagePermission.IMPORT),

            // Java permissions.
            RuntimePermission("*"),
            ReflectPermission("*"),
            NetPermission("*"),
            LinkPermission("hard"),
            LinkPermission("symbolic"),
            ManagementPermission("control"),
            ManagementPermission("monitor"),
            PropertyPermission("*", "read,write"),
            SocketPermission("*", "accept,connect,listen"),
            FilePermission("<<ALL FILES>>", "read,write,execute,delete,readlink")
        ))
        securityManager.grantPermissions("FLOW/*", listOf(
            PackagePermission("net.corda.v5.*", PackagePermission.IMPORT),
            ServicePermission("(location=FLOW/*)", ServicePermission.GET),
            ServicePermission("net.corda.v5.*", ServicePermission.GET)
        ))
        securityManager.denyPermissions("*", listOf(
            ServicePermission(PermissionAdmin::class.java.name, ServicePermission.REGISTER)
        ))

        val baseDirectory = Paths.get(System.getProperty("app.name", System.getProperty("user.dir", "."))).toAbsolutePath()
        sandboxSetup.configure(bundleContext, Files.createDirectories(baseDirectory))
    }
}
