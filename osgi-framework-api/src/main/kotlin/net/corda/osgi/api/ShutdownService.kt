package net.corda.osgi.api

import org.osgi.framework.Bundle

/**
 * OSGi service published by [net.corda.osgi.framework.]
 */
interface ShutdownService {

    fun shutdown(bundle: Bundle)

}