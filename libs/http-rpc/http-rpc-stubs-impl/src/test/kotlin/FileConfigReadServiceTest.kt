package net.corda.libs.config

import net.corda.configuration.read.ConfigurationHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FileConfigReadServiceTest {

    @Test
    fun `starting the service will update listeners with the config` () {
        val service = FileConfigReadService()

        val configHandlerMock: ConfigurationHandler = mock()
        service.use {
            it.registerForUpdates(configHandlerMock)
        }
        verify(configHandlerMock, times(1)).onNewConfiguration(eq(setOf("http-rpc-gateway.conf")), argThat { get("http-rpc-gateway.conf")!!.hasPath("httpRpcSettings") })
    }
}