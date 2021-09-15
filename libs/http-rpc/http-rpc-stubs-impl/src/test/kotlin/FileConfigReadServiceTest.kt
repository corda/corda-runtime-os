package net.corda.libs.config

import net.corda.configuration.read.ConfigurationHandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FileConfigReadServiceTest {

    @Test
    fun `starting the service will update listeners with the config` () {
        val service = FileConfigReadService()

        val configHandler1: ConfigurationHandler = mock()
        val configHandler2: ConfigurationHandler = mock()
        service.use {
            it.registerForUpdates(configHandler1)
            verify(configHandler1, never()).onNewConfiguration(any(), any())

            it.start()
            verify(configHandler1, times(1))
                    .onNewConfiguration(eq(setOf("http-rpc-gateway.conf")),
                            argThat { get("http-rpc-gateway.conf")!!.hasPath("httpRpcSettings") })

            it.registerForUpdates(configHandler2)
            verify(configHandler2, times(1))
                    .onNewConfiguration(eq(setOf("http-rpc-gateway.conf")),
                            argThat { get("http-rpc-gateway.conf")!!.hasPath("httpRpcSettings") })
        }

    }
}