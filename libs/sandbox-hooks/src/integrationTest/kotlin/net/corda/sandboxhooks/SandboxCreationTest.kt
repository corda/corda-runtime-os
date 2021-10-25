package net.corda.sandboxhooks

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the creation of sandbox groups. */
@ExtendWith(ServiceExtension::class)
class SandboxCreationTest {
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader
    }

    @Test
    fun sandboxGroupHasASandboxPerCpk() {
        assertThat(sandboxLoader.group1.sandboxes).hasSize(2)
        assertThat(sandboxLoader.group2.sandboxes).hasSize(1)
    }
}
