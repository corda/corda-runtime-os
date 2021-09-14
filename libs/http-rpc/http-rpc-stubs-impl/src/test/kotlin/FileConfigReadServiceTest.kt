package net.corda.libs.config

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import org.junit.jupiter.api.Test

class FileConfigReadServiceTest {
    @Test
    fun `starting the service will update listeners with the config` () {
        val service = FileConfigReadService()

        val listener = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
            println (changedKeys)
            println (currentConfigurationSnapshot)
        }

        service.registerCallback(listener)
        service.start()
    }
}