package net.corda.libs.configuration.read.file

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("TooGenericExceptionCaught")
class FileConfigReadServiceImpl(
    private val configurationRepository: ConfigRepository,
    private val bootstrapConfig: Config
) : ConfigReadService {

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

    override fun registerCallback(configListener: ConfigListener): AutoCloseable {
        val sub = ConfigListenerSubscription(this)
        configUpdates[sub] = configListener
        if (snapshotReceived) {
            val configs = configurationRepository.getConfigurations()
            configListener.onUpdate(configs.keys, configs)
        }
        return sub
    }

    private fun readConfigFile(): Config {
        return try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            val configFilePath = bootstrapConfig.getString(CONFIG_FILE_NAME)
            ConfigFactory.parseURL(File(configFilePath).toURI().toURL(), parseOptions).resolve()
        } catch (e: ConfigException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        } catch (e: IOException) {
            log.error(e.message, e)
            ConfigFactory.empty()
        }
    }

    private fun unregisterCallback(sub: ConfigListenerSubscription) {
        configUpdates.remove(sub)
    }

    private class ConfigListenerSubscription(private val configReadService: FileConfigReadServiceImpl) : AutoCloseable {
        override fun close() {
            configReadService.unregisterCallback(this)
        }
    }
}


