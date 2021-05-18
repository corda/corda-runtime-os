package net.corda.osgi.api

import org.osgi.framework.Bundle

interface ShutdownService {

    fun shutdown(bundle: Bundle)

}