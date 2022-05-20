package net.corda.example.vnode

import java.io.FilePermission
import java.lang.management.ManagementPermission
import java.lang.reflect.ReflectPermission
import java.net.NetPermission
import java.net.SocketPermission
import java.nio.file.Files
import java.nio.file.LinkPermission
import java.nio.file.Paths
import java.security.AllPermission
import java.security.Permission
import java.util.PropertyPermission
import net.corda.securitymanager.ConditionalPermission
import net.corda.securitymanager.ConditionalPermission.Access
import net.corda.securitymanager.ConditionalPermission.Access.ALLOW
import net.corda.securitymanager.ConditionalPermission.Access.DENY
import net.corda.securitymanager.SecurityManagerService
import net.corda.testing.sandboxes.SandboxSetup
import org.osgi.framework.AdminPermission
import org.osgi.framework.BundleContext
import org.osgi.framework.PackagePermission
import org.osgi.framework.PackagePermission.EXPORTONLY
import org.osgi.framework.PackagePermission.IMPORT
import org.osgi.framework.ServicePermission
import org.osgi.framework.ServicePermission.GET
import org.osgi.framework.ServicePermission.REGISTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.BundleLocationCondition
import org.osgi.service.condpermadmin.ConditionInfo
import org.osgi.service.permissionadmin.PermissionAdmin
import org.osgi.service.permissionadmin.PermissionInfo

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
        securityManager.updatePermissions(listOf(
            bundleLocationPermission("*", DENY,
                ServicePermission(PermissionAdmin::class.java.name, REGISTER)
            ),
            bundleLocationPermission("FLOW/*", ALLOW,
                PackagePermission("net.corda.v5.*", IMPORT),
                ServicePermission("(location=FLOW/*)", GET),
                ServicePermission("net.corda.v5.*", GET),
                RuntimePermission("accessClassInPackage.net.corda.v5.*")
            ),
            bundleLocationPermission("FLOW/*", DENY,
                // OSGi permissions.
                AdminPermission(),
                ServicePermission("*", GET),
                ServicePermission("net.corda.v5.*", REGISTER),
                PackagePermission("net.corda", "$EXPORTONLY,$IMPORT"),
                PackagePermission("net.corda.*", "$EXPORTONLY,$IMPORT"),

                // Prevent the FLOW sandboxes from importing these packages,
                // which effectively forbids them from executing most OSGi code.
                PackagePermission("org.osgi.framework", IMPORT),
                PackagePermission("org.osgi.service.component", IMPORT),

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
            ),
            bundleLocationPermission("*", ALLOW, AllPermission())
        ), clear = true)

        val baseDirectory = Paths.get(System.getProperty("app.name", System.getProperty("user.dir", "."))).toAbsolutePath()
        sandboxSetup.configure(bundleContext, Files.createDirectories(baseDirectory))
    }

    /**
     * Creates [ConditionalPermission] with [BundleLocationCondition] and given [permissions]
     */
    private fun bundleLocationPermission(location: String, access: Access, vararg permissions: Permission) =
        ConditionalPermission(
            ConditionInfo(BundleLocationCondition::class.java.name, arrayOf(location)),
            permissions.map { PermissionInfo(it::class.java.name, it.name, it.actions) }.toTypedArray(),
            access
        )
}
