package net.corda.sample.impl.hello

import net.corda.sample.impl.hello.HelloWorld
import org.osgi.service.component.annotations.Component

@Component
class HelloWorldImpl: HelloWorld {
    override fun sayHello() {
        println("Hello world!")
    }
}

