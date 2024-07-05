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

    companion object {
        const val MULTIPLE_MGM_ERROR_MESSAGE = "Invalid number of MGM nodes defined, can only specify one."
    }

    val vNodes: List<VNode>

    init {
        val mapper = ObjectMapper()
        vNodes = try {
            FileInputStream(configFilePath).use {
                mapper.readValue(it, object : TypeReference<List<VNode>>() {})
            }
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to read network configuration file, with exception: $e")
        }
    }

    val x500Names = vNodes.map { it.x500Name }

    fun getMgmNode(): VNode? {
        val mgmNodes = vNodes.filter { it.mgmNode.toBoolean() }
        if (mgmNodes.size > 1) {
            throw CordaRuntimeGradlePluginException(MULTIPLE_MGM_ERROR_MESSAGE)
        }
        return mgmNodes.singleOrNull()
    }

    fun getVNodesWhoAreNotMgm(): List<VNode> {
        return vNodes.filter { it != getMgmNode() }
    }

    val mgmNodeIsPresentInNetworkDefinition: Boolean = getMgmNode() != null
}