package com.example

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component(name = "helloworld.3")
class HelloWorldFlowThree : Flow<String> {
    override fun call(): String {
        return "hello world three"
    }
}
