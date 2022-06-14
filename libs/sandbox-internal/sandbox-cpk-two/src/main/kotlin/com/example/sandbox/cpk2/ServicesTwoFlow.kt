package com.example.sandbox.cpk2

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Subflow
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns services visible to this sandbox. */
@Suppress("unused")
@Component(name = "services.two.flow")
class ServicesTwoFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Subflow<List<Class<out Any>>> {
    private val logger = loggerFor<ServicesTwoFlow>()

    override fun call() = sandboxQuery.getAllServiceClasses()
}