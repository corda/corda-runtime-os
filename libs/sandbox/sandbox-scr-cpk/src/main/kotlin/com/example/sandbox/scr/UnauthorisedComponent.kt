package com.example.sandbox.scr

import net.corda.v5.application.flows.Flow
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime

@Suppress("unused")
@Component(name = "unauthorised.scr.flow")
class UnauthorisedComponent @Activate constructor(
    @Reference
    private val scr: ServiceComponentRuntime
) : Flow<List<String>> {
    init {
        loggerFor<UnauthorisedComponent>().info("Activated!")
    }

    override fun call(): List<String> {
        return scr.getComponentDescriptionDTOs().map(Any::toString)
    }
}
