@file:Suppress("unused")
package com.example.sandbox.cpk3

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.framework.ServiceEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(name = "service-event.three.flow")
class ServiceEventThreeFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<ServiceEvent>> {
    override fun call(): List<ServiceEvent> {
        return sandboxQuery.getServiceEvents()
    }
}
