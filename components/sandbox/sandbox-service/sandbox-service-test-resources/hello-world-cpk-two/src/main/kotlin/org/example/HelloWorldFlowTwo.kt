package org.example

import net.corda.v5.application.flows.Flow
import org.osgi.service.component.annotations.Component

@Component(name = "helloworld.2")
class HelloWorldFlowTwo : Flow<String> {
    override fun call(): String {
        return "hello world two"
    }
}
