package net.corda.libs.configuration.read.file

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FileConfigReaderImpl(
    private val configurationRepository: ConfigRepository,
    private val bootstrapConfig: SmartConfig,
    private val smartConfigFactory: SmartConfigFactory,
) : ConfigReader {

    companion object {
        private val log = contextLogger()

        @VisibleForTesting
        const val CONFIG_FILE_NAME = "config.file"
    }

    @Volatile
    private var stopped = false

    private val lock = ReentrantLock()

    private val configUpdates = Collections.synchronizedMap(mutableMapOf<ConfigListenerSubscription, ConfigListener>())

    override val isRunning: Boolean
        get() {
            return !stopped
        }

    override fun start() {
        lock.withLock {
            storeFileConfig()
            if(!bootstrapConfig.isEmpty) storeBootstrapConfig()
            stopped = false
            val configs = configurationRepository.getConfigurations()
            configUpdates.forEach { it.value.onUpdate(configs.keys, configs) }
        }
    }

    override fun stop() {
        lock.withLock {
            if (!stopped) {
                stopped = true
            }
        }
    }

    override fun close() {
        lock.withLock {
            stop()
            configUpdates.clear()
        }
    }

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        val sub = ConfigListenerSubscription(configUpdates)
        configUpdates[sub] = configListener
        if (isRunning) {
            val configs = configurationRepository.getConfigurations()
            configListener.onUpdate(configs.keys, configs)
        }
        return sub
    }

    private fun storeFileConfig() {
        val config = parseConfigFile()
        for (packageKey in config.root().keys) {
            val packageConfig = config.getConfig(packageKey)
            for (componentKey in packageConfig.root().keys) {
                configurationRepository.updateConfiguration("$packageKey.$componentKey", packageConfig.getConfig(componentKey))
            }
        }
    }

    private fun storeBootstrapConfig() {
        configurationRepository.updateConfiguration("corda.boot", bootstrapConfig)
    }

    private fun parseConfigFile(): SmartConfig {
        val conf = try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            val configFilePath = bootstrapConfig.getString(CONFIG_FILE_NAME)
            ConfigFactory.parseURL(File(configFilePath).toURI().toURL(), parseOptions)

        } catch (e: ConfigException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        } catch (e: IOException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        }
        return smartConfigFactory.create(conf)
    }

    private class ConfigListenerSubscription(private val configUpdates: MutableMap<ConfigListenerSubscription, ConfigListener>) :
        AutoCloseable {
        override fun close() {
            configUpdates.remove(this)
        }
    }
}


