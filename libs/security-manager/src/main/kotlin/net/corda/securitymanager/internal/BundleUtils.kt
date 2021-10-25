package net.corda.securitymanager.internal

import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component

// TODO - Describe.
@Component(service = [BundleUtils::class])
open class BundleUtils {
    /** Retrieves the location of the [klass]'s bundle. */
    open fun getBundleLocation(klass: Class<*>): String? = FrameworkUtil.getBundle(klass).location
}