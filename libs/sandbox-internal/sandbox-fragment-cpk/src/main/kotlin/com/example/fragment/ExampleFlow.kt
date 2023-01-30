package com.example.fragment

import net.corda.v5.application.flows.SubFlow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.util.Properties

@Suppress("unused")
@Component(name = "sandbox.fragment.flow")
class ExampleFlow @Activate constructor(): SubFlow<String> {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        logger.info("Activated")
    }

    override fun call(): String {
        return Properties().also { p ->
            this::class.java.getResourceAsStream("fragment.properties")?.use(p::load)
        }.getProperty("com.example.fragment.message", "<NOTHING>")
    }
}
