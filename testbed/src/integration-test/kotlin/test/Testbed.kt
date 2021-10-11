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

        val sandboxCreationService = sandboxLoader.sandboxCreationService
        val cpkHash = sandboxLoader.cpk.cpkHash

        (0..3).forEach {
            val sandboxGroup = sandboxCreationService.createSandboxGroup(setOf(cpkHash))
            sandboxCreationService.unloadSandboxGroup(sandboxGroup)
        }

        val sandboxAdminService = sandboxLoader.sandboxAdminService
        println(sandboxAdminService.zombieBundles)
    }
}
