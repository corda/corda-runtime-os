package net.corda.securitymanager

import org.osgi.framework.CapabilityPermission
import org.osgi.framework.PackagePermission
import org.osgi.framework.ServicePermission

// Filter that identifies sandbox bundles based on the security domain encoded in their location.
internal const val SANDBOX_SECURITY_DOMAIN_FILTER = "sandbox/*"
internal const val ALL = "*"
internal val PACKAGE_PERMISSION_NAME = PackagePermission::class.java.name
internal val CAPABILITY_PERMISSION_NAME = CapabilityPermission::class.java.name
internal val SERVICE_PERMISSION_NAME = ServicePermission::class.java.name