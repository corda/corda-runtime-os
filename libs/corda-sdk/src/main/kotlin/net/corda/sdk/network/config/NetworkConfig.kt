package net.corda.sdk.network.config

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.FileInputStream

/**
 * Class which reads in a network config file, parses it and exposes the list of VNodes and x500Names required.
 */
class NetworkConfig(val configFilePath: String) {

    companion object {
        const val MULTIPLE_MGM_ERROR_MESSAGE = "Invalid number of MGM nodes defined, can only specify one."
        const val DUPLICATE_X500_NAMES_ERROR_MESSAGE = "Duplicate X.500 names found in network configuration file."
    }

    val vNodes: List<VNode>

    init {
        val mapper = ObjectMapper()
        vNodes = try {
            FileInputStream(configFilePath).use {
                mapper.readValue(it, object : TypeReference<List<VNode>>() {})
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to read network configuration file, with exception: $e")
        }
        validateUniqueX500Names()
    }

    val x500Names get() = vNodes.map { it.x500Name }

    private fun validateUniqueX500Names() {
        require(x500Names.size == x500Names.toSet().size) { DUPLICATE_X500_NAMES_ERROR_MESSAGE }
    }

    val memberNodes: List<VNode>
        get() = vNodes.filter { it.serviceX500Name.isNullOrBlank() && !it.mgmNode.toBoolean() }

    fun getMgmNode(): VNode? {
        val mgmNodes = vNodes.filter { it.mgmNode.toBoolean() }
        require(mgmNodes.size <= 1) { MULTIPLE_MGM_ERROR_MESSAGE }
        return mgmNodes.singleOrNull()
    }

    fun getVNodesWhoAreNotMgm(): List<VNode> {
        return vNodes.filter { it != getMgmNode() }
    }

    val mgmNodeIsPresentInNetworkDefinition: Boolean = getMgmNode() != null
}
