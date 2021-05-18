package net.corda.osgi.framework.api

import org.osgi.framework.Bundle

interface ShutdownService {

    fun shutdown(bundle: Bundle)

}