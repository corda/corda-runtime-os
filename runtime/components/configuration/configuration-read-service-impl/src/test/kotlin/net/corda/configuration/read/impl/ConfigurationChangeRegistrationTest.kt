package net.corda.configuration.read.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ConfigurationChangeRegistrationTest {

    @Test
    fun `Invoking the configuration change registration calls the underlying handler`() {
        val keys = setOf("FOO")
        val config = mapOf<String, SmartConfig>("FOO" to mock())
        val reg = ConfigurationChangeRegistration(mock()) { k, c ->
            assertEquals(keys, k)
            assertEquals(config, c)
        }
        reg.invoke(keys, config)
    }

    @Test
    fun `Invoking a closed registration does nothing`() {
        val coordinator = mock<LifecycleCoordinator>()
        val reg = ConfigurationChangeRegistration(coordinator) { _, _ ->
            fail("Registration handler was called when the registration was closed")
        }
        reg.close()
        verify(coordinator).postEvent(ConfigRegistrationRemove(reg))
        reg.invoke(setOf("FOO"), mapOf("FOO" to mock()))
    }

    @Test
    fun `Exceptions thrown while handling config changes are ignored`() {
        val keys = setOf("FOO")
        val config = mapOf<String, SmartConfig>("FOO" to mock())
        val reg = ConfigurationChangeRegistration(mock()) { _, _ ->
            throw IllegalArgumentException("This handler is bad")
        }
        reg.invoke(keys, config)
    }
}