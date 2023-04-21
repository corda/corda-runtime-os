package com.example.sandbox.scr

import net.corda.v5.application.flows.SubFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.runtime.ServiceComponentRuntime
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(name = "unauthorised.scr.flow")
class UnauthorisedComponent @Activate constructor(
    @Reference
    private val scr: ServiceComponentRuntime
) : SubFlow<List<String>> {
    init {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        logger.info("Activated!")
    }

    override fun call(): List<String> {
        return scr.getComponentDescriptionDTOs().map(Any::toString)
    }
}
