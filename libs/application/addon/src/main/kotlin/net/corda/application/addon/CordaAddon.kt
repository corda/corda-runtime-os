package net.corda.application.addon

import org.osgi.framework.FrameworkUtil


interface CordaAddon {
    val name: String
    val licence: String
    val vendor: String
    val description: String
    val version: String
        get() {
            val bundle = FrameworkUtil.getBundle(this::class.java.classLoader)
            if(bundle.isPresent) return bundle.get().version.toString()
            return "UNKNOWN"
        }
}