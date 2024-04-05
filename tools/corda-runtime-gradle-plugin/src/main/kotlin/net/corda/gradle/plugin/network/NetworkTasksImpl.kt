package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.VNode
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.getExistingNodes
import net.corda.gradle.plugin.retry
import net.corda.gradle.plugin.rpcWait
import java.time.Duration

class NetworkTasksImpl(var pc: ProjectContext) {

    /**
     * Creates vnodes specified in the config if they don't already exist.
     */
    fun createVNodes() {

        // Represents the list of VNodes as specified in the network Config json file (static-network-config.json)
        val requiredNodes: List<VNode> = pc.networkConfig.vNodes

        val existingVNodes = getExistingNodes(pc)

        // Check if each required vnode already exist, if not create it.
        val nodesToCreate = requiredNodes.filter { vn ->
            !existingVNodes.any { en ->
                en.holdingIdentity?.x500Name.equals(vn.x500Name) &&
                        en.cpiIdentifier?.cpiName.equals(vn.cpi)
            }
        }
        nodesToCreate.forEach {
            val cpiUploadFilePath = if (it.serviceX500Name == null) pc.corDappCpiChecksumFilePath else pc.notaryCpiChecksumFilePath

            VNodeHelper().createVNode(
                pc.cordaClusterURL,
                pc.cordaRestUser,
                pc.cordaRestPassword,
                it,
                cpiUploadFilePath
            )
            // Check if the virtual node has been created.
            retry(timeout = Duration.ofMillis(30000)) {
                getExistingNodes(pc).singleOrNull { knownVNode ->
                    knownVNode.holdingIdentity?.x500Name.equals(it.x500Name)
                } ?: throw CordaRuntimeGradlePluginException("Failed to create VNode: ${it.x500Name}")
            }
        }
    }

    /**
     * Checks if the required virtual nodes have been registered and if not registers them.
     */
    fun registerVNodes() {
        // Represents the list of VNodes as specified in the network Config json file (static-network-config.json)
        val requiredNodes: List<VNode> = pc.networkConfig.vNodes

        // There appears to be a delay between the successful post /virtualnodes synchronous call and the
        // vnodes being returned in the GET /virtualnodes call. Putting a thread wait here as a quick fix
        // as this will move to async mechanism post beta2. see CORE-12153
        rpcWait(3000)

        val existingNodes = getExistingNodes(pc)
        val helper = VNodeHelper()

        requiredNodes.forEach { vn ->
            val match = helper.findMatchingVNodeFromList(existingNodes, vn)

            val shortHash = try {
                match.holdingIdentity!!.shortHash!!
            } catch (e: Exception) {
                throw CordaRuntimeGradlePluginException("Cannot read ShortHash for virtual node '${vn.x500Name}'")
            }

            if (!helper.checkVNodeIsRegistered(
                    pc.cordaClusterURL,
                    pc.cordaRestUser,
                    pc.cordaRestPassword,
                    shortHash
                )
            ) {
                pc.logger.quiet("Registering vNode: ${vn.x500Name} with shortHash: $shortHash")
                helper.registerVNode(
                    pc.cordaClusterURL,
                    pc.cordaRestUser,
                    pc.cordaRestPassword,
                    vn,
                    shortHash,
                    pc.vnodeRegistrationTimeout
                )
                pc.logger.quiet("VNode ${vn.x500Name} with shortHash $shortHash registered.")
            }
        }
    }
}