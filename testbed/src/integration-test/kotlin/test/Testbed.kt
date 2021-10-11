package test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

@ExtendWith(ServiceExtension::class)
class Testbed {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun testbed() {
        println("jjj")
        println(sandboxLoader.cpk.type)
    }
}
