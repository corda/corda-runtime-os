package net.corda.gradle.plugin.configuration

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.gradle.plugin.dtos.VNode
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.io.FileInputStream

/**
 * Class which reads in a network config file, parses it and exposes the list of VNodes and x500Names required.
 */
class NetworkConfig(val configFilePath: String) {

    var vNodes: List<VNode>

    init {
        val mapper = ObjectMapper()
        vNodes = try {
            val fis = FileInputStream(configFilePath)
            mapper.readValue(fis, object : TypeReference<List<VNode>>(){})
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to read static network configuration file, with exception: $e")
        }
    }

    var x500Names = vNodes.map { it.x500Name }
}