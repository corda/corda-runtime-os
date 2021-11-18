package com.example.sandbox.library

import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.ServiceEvent

/** An interface for retrieving services, bundles and events visible to a bundle. */
interface SandboxQuery {
    fun getAllServiceClasses(): List<Class<out Any>>

    fun getAllBundles(): List<Bundle>

    fun getBundleEvents(): List<BundleEvent>

    fun getServiceEvents(): List<ServiceEvent>
}
