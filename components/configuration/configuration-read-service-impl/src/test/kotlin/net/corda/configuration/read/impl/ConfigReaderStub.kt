package net.corda.configuration.read.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import org.junit.jupiter.api.Assertions.assertEquals

class ConfigReaderStub : ConfigReader {

    private val listeners = mutableSetOf<ConfigListener>()

    private var config = ConfigFactory.empty()

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        listeners.add(configListener)
        return AutoCloseable { listeners.remove(configListener) }
    }

    override var isRunning = false

    override fun start() {
        isRunning = true
    }

    override fun stop() {
        isRunning = false
    }

    fun postEvent(keys: Set<String>, config: Map<String, SmartConfig>) {
        listeners.forEach {
            it.onUpdate(keys, config)
        }
    }

    fun assertNoListeners() {
        assertEquals(setOf<ConfigListener>(), listeners)
    }

    fun withBootstrapConfig(bootstrapConfig: Config) : ConfigReaderStub {
        config = bootstrapConfig
        return this
    }

    fun assertBootstrapConfig(expected: Config) {
        assertEquals(expected, config)
    }
}