@file:Suppress("unused")
package com.example.sandbox.library.impl

import com.example.sandbox.library.SandboxQuery
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.ServiceEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections.unmodifiableList

/** An implementation of the [SandboxQuery] interface. */
@Component(name = "sandbox.query", immediate = true)
class SandboxQueryImpl @Activate constructor(
    private val context: BundleContext
) : SandboxQuery {
    private val logger: Logger = LoggerFactory.getLogger(SandboxQueryImpl::class.java)

    private val bundleEvents = mutableListOf<BundleEvent>()
    private val serviceEvents = mutableListOf<ServiceEvent>()

    init {
        logger.info("Activating!")
        context.addBundleListener(bundleEvents::add)
        context.addServiceListener(serviceEvents::add)
    }

    @Deactivate
    fun done() {
        logger.info("Deactivating!")
    }

    override fun getAllServiceClasses(): List<Class<out Any>> {
        return context.getAllServiceReferences(null, null)
            .mapNotNull { ref -> context.getService(ref) }
            .map { it::class.java }
    }

    override fun getAllBundles(): List<Bundle> {
        return context.bundles.toList()
    }

    override fun getBundleEvents(): List<BundleEvent> {
        return unmodifiableList(ArrayList(bundleEvents))
    }

    override fun getServiceEvents(): List<ServiceEvent> {
        return unmodifiableList(ArrayList(serviceEvents))
    }
}
