package net.corda.libs.config

import net.corda.libs.configuration.read.ConfigListener
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FileConfigReadServiceTest {

    private val listenerMock: ConfigListener = mock()
    @Test
    fun `starting the service will update listeners with the config` () {
        val service = FileConfigReadService()

        service.registerCallback(listenerMock)
        service.start()

        verify(listenerMock, times(1)).onUpdate(eq(setOf("node.conf")), argThat { get("node.conf")!!.hasPath("httpRpcSettings") })
    }
}