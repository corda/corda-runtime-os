package net.corda.sample.hello.impl

import net.corda.sample.api.hello.HelloWorld
import org.osgi.service.component.annotations.Component

@Component
class HelloWorldImpl: HelloWorld {
    override fun sayHello(): String {
        val hi = "Hello world!"
        println(hi)
        return hi
    }
}

