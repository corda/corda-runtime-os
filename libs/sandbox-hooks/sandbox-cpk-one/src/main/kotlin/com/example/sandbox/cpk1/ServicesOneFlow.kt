@file:Suppress("unused")
package com.example.sandbox.cpk1

import com.example.sandbox.library.SandboxQuery
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(name = "services.one.flow")
class ServicesOneFlow @Activate constructor(
    @Reference(target = "(component.name=sandbox.query)")
    private val sandboxQuery: SandboxQuery
) : Flow<List<Class<out Any>>> {
    private val logger = loggerFor<ServicesOneFlow>()

    init {
        logger.info("Activating!")
    }

    override fun call(): List<Class<out Any>> {
        return sandboxQuery.getAllServiceClasses()
    }
}
