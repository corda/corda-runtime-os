package net.corda.sample.goodbye

import net.corda.sample.impl.hello.HelloWorld
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

fun main() {
    GoodbyeWorldTest().callHelloWorld()
}

@ExtendWith(ServiceExtension::class)
class GoodbyeWorldTest {

    @InjectService(timeout = 1000, filter = "(component.name=helloworld)")
    private var helloWorld: HelloWorld? = null

    @Test
    fun callHelloWorld() {
        helloWorld?.sayHello()
    }

}
