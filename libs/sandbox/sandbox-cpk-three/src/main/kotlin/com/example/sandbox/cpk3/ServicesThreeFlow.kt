package com.example.sandbox.cpk3

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/** Returns services visible to this sandbox. */
@Suppress("unused")
@Component(name = "services.three.flow")
class ServicesThreeFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<Class<out Any>>> {
    override fun call() = sandboxQuery.getAllServiceClasses()
}