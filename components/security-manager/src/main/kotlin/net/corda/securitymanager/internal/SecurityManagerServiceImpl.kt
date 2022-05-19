package net.corda.securitymanager.internal

import net.corda.securitymanager.ConditionalPermission
import net.corda.securitymanager.ConditionalPermission.Access.ALLOW
import net.corda.securitymanager.ConditionalPermission.Access.DENY
import net.corda.securitymanager.SecurityManagerException
import net.corda.securitymanager.SecurityManagerService
import net.corda.v5.base.util.contextLogger
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.condpermadmin.ConditionalPermissionAdmin
import org.osgi.service.condpermadmin.ConditionalPermissionInfo
import java.io.IOException
import java.io.InputStream
import java.security.Security
import java.text.ParseException

/** An implementation of [SecurityManagerService]. */
@Suppress("unused")
@Component(immediate = true, service = [SecurityManagerService::class])
class SecurityManagerServiceImpl @Activate constructor(
    @Reference
    private val conditionalPermissionAdmin: ConditionalPermissionAdmin,
    bundleContext: BundleContext
) : SecurityManagerService {
    companion object {
        private val log = contextLogger()
        const val PACKAGE_ACCESS_PROPERTY = "package.access"
        private const val DEFAULT_SECURITY_POLICY = "high_security.policy"
    }

    // The OSGi security manager that is installed at framework start.
    private val osgiSecurityManager: SecurityManager? = System.getSecurityManager()

    // The current Corda security manager.
    private var cordaSecurityManager: CordaSecurityManager? = null

    init {
        enablePackageAccessPermission("net.corda.")
        startRestrictiveMode()
        val url = bundleContext.bundle.getResource(DEFAULT_SECURITY_POLICY)
        log.info("Applying default security policy ($DEFAULT_SECURITY_POLICY)")
        val policy = readPolicy(url.openConnection().getInputStream())
        updatePermissions(policy, clear = true)
    }

    /**
     * Enables "accessClassInPackage" [RuntimePermission] for specified [packageName]
     * @param packageName Package name. Name can use an asterisk that may appear by itself, or if immediately preceded
     * by a ".", it may appear at the end of the name.
     */
    private fun enablePackageAccessPermission(packageName: String) {
        val packageAccess = Security.getProperty(PACKAGE_ACCESS_PROPERTY)
        val separator = if (packageAccess.isNotBlank()) "," else ""
        Security.setProperty(PACKAGE_ACCESS_PROPERTY, packageAccess + separator + packageName)
    }

    override fun startRestrictiveMode() {
        cordaSecurityManager?.stop()
        log.info("Starting restrictive Corda security manager.")
        cordaSecurityManager = RestrictiveSecurityManager(conditionalPermissionAdmin, osgiSecurityManager)
    }

    override fun updatePermissions(permissions: List<ConditionalPermission>, clear: Boolean) {
        cordaSecurityManager?.updateConditionalPerms(permissions, clear)
            ?: throw SecurityManagerException("No Corda security manager is currently running.")
    }

    /**
     * Reads security policy from [inputStream] and returns list of [ConditionalPermissionInfo]. Policy contains text
     * block(s) of encoded [ConditionalPermissionInfo], for example:
     *
     *     ALLOW {
     *     [org.osgi.service.condpermadmin.BundleLocationCondition "FLOW/ *"]
     *     (java.io.FilePermission "<<ALL FILES>>" "read")
     *     } "Allow read access to all files"
     *
     * @param inputStream Input stream
     * @return List of [ConditionalPermissionInfo]
     * @see [ConditionalPermissionAdmin.newConditionalPermissionInfo]
     */
    @Throws(IOException::class)
    override fun readPolicy(inputStream: InputStream): List<ConditionalPermission> {
        val policy = mutableListOf<ConditionalPermission>()
        val permission = StringBuilder()
        inputStream.bufferedReader().forEachLine { line ->
            if (!line.startsWith("#")) {
                permission.append(line)
                if (line.contains("}")) {
                    policy.add(parsePermission(permission.toString()))
                    permission.clear()
                }
            }
        }
        return policy
    }

    /**
     * Creates a new ConditionalPermission from the specified encoded [permission] string
     *
     * @param permission The encoded [ConditionalPermissionInfo]. White space in the encoded ConditionalPermissionInfo is
     * ignored. The access decision value in the encoded ConditionalPermissionInfo must be evaluated case insensitively.
     * If the encoded ConditionalPermissionInfo does not contain the optional name, null must be used for the name and a
     * unique name will be generated.
     * @throws [ParseException] if given [permission] string can't be parsed
     */
    private fun parsePermission(permission: String): ConditionalPermission {
        try {
            with (conditionalPermissionAdmin.newConditionalPermissionInfo(permission)) {
                val access = if (accessDecision == ConditionalPermissionInfo.ALLOW) ALLOW else DENY
                return ConditionalPermission(name, conditionInfos, permissionInfos, access)
            }
        } catch (e: IllegalArgumentException) {
            throw ParseException("Error parsing permission '$permission'", -1)
        }
    }
}