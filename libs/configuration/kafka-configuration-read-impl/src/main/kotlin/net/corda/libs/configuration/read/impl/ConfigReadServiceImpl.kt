package net.corda.libs.configuration.read.impl

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.CordaConfigurationUpdate
import org.osgi.service.component.annotations.Component

@Component(immediate = true, service = [ConfigReadService::class])
class ConfigReadServiceImpl(private val configurationList: Map<String, Config>) : ConfigReadService {

    companion object {
        private val objectMapper = ObjectMapper()
            .registerModule(KotlinModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    override fun getConfiguration(componentName: String): Config {
        return configurationList[componentName] ?: throw IllegalArgumentException("Unknown component: $componentName")
    }

    override fun <T> parseConfiguration(componentName: String, clazz: Class<T>): T {
        return try {
            val config = getConfiguration(componentName)
            objectMapper.convertValue(config, clazz)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Throwable) {
            throw IllegalArgumentException("Cannot deserialize configuration for $clazz", e)
        }
    }

    override fun registerCallback(callback: CordaConfigurationUpdate) {
        TODO("Not yet implemented")
    }

}