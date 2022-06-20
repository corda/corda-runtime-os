package com.example.sandbox.cpk1

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.SubFlow
import org.osgi.framework.ServiceEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns a list of [ServiceEvent]s visible to this sandbox. */
@Suppress("unused")
@Component(name = "service-event.one.flow")
class ServiceEventOneFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : SubFlow<List<ServiceEvent>> {
    override fun call() = sandboxQuery.getServiceEvents()
}
