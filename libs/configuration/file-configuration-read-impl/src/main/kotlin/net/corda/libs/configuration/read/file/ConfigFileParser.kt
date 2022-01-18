package net.corda.libs.configuration.read.file

import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.io.IOException
import java.net.URL

class ConfigFileParser(private val smartConfigFactory: SmartConfigFactory) {

    companion object {
        private val logger = contextLogger()
    }

    fun parseFile(file: URL): Map<String, SmartConfig> {
        val conf = try {
            val parseOptions = ConfigParseOptions.defaults().setAllowMissing(false)
            ConfigFactory.parseURL(file, parseOptions)
        } catch (e: ConfigException) {
            logger.error(e.message, e)
            ConfigFactory.empty()
        } catch (e: IOException) {
            logger.error(e.message, e)
            ConfigFactory.empty()
        }
        val smartConf = smartConfigFactory.create(conf)
        val configMap = mutableMapOf<String, SmartConfig>()
        for (packageKey in smartConf.root().keys) {
            val packageConfig = smartConf.getConfig(packageKey)
            for (componentKey in packageConfig.root().keys) {
                val componentConfig = packageConfig.getConfig(componentKey)
                configMap["$packageKey.$componentKey"] = componentConfig
            }
        }
        logger.debug { "Parsed the following config from file $file: $configMap" }
        return configMap
    }
}