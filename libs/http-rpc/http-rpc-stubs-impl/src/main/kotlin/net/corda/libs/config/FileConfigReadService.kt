package net.corda.libs.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import java.io.IOException
import java.util.Collections

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileConfigReadService : ConfigurationReadService {

    private companion object {
        private val log = contextLogger()
    }

    private val configFile = "http-rpc-gateway.conf"

    @Volatile
    private var stopped = true

    private val lock = ReentrantLock()

    private val config = parseConfigFile()

    private val configUpdates = Collections.synchronizedMap(mutableMapOf<ConfigListenerSubscription, ConfigurationHandler>())

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        val sub = ConfigListenerSubscription(this)
        configUpdates[sub] = configHandler
        if (isRunning) {
            configHandler.onNewConfiguration(setOf(configFile), mapOf(configFile to config))
        }
        return sub
    }

    override fun bootstrapConfig(config: Config) {

    }

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            configUpdates.forEach { it.value.onNewConfiguration(setOf(configFile), mapOf(configFile to config)) }
            stopped = false
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                configUpdates.clear()
                stopped = true
            }
        }
    }

    private fun unregisterCallback(sub: ConfigListenerSubscription) {
        configUpdates.remove(sub)
    }

    private fun parseConfigFile(): Config {

        val fileRes = FileConfigReadService::class.java.getResource("/$configFile")
                ?: throw CordaRuntimeException("File $configFile not found in resources.")

        return try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            ConfigFactory.parseURL(fileRes, parseOptions).resolve()
        } catch (e: ConfigException) {

            log.error(e.message, e)
            ConfigFactory.empty()
        } catch (ioe: IOException) {
            log.error(ioe.message, ioe)
            ConfigFactory.empty()
        }
    }

    private class ConfigListenerSubscription(private val configReadService: FileConfigReadService) : AutoCloseable {
        override fun close() {
            configReadService.unregisterCallback(this)
        }
    }
}

