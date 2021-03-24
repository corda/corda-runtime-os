package net.corda.sample.hello.impl

import net.corda.sample.api.hello.HelloWorld
import org.osgi.service.component.annotations.Component

@Component
class HelloWorldImpl: HelloWorld {
    override fun sayHello() {
        println("Hello world!")
    }
}

