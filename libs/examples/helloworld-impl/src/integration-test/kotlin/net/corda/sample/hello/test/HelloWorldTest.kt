package net.corda.sample.hello.test

import net.corda.sample.api.hello.HelloWorld
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class HelloWorldTest {

    @InjectService(timeout = 1000)
    lateinit var helloWorld: HelloWorld

    @Test
    fun callHelloWorld() {
        assertThat(helloWorld.sayHello()).isEqualTo("Hello world!")
    }

}
