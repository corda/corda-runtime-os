package com.example.sandbox.cpk2

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.loggerFor
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Component

/** Returns a list of services visible to this sandbox. */
@Suppress("unused")
@Component(name = "services.two.flow")
class ServicesTwoFlow: Flow<List<Class<out Any>>> {
    private val logger = loggerFor<ServicesTwoFlow>()

    override fun call(): List<Class<out Any>> {
        val bundleContext = FrameworkUtil.getBundle(this::class.java).bundleContext
        return bundleContext.getAllServiceReferences(null, null)
            .mapNotNull { ref -> bundleContext.getService(ref) }
            .map { it::class.java }
    }
}
