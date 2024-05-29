package net.corda.gradle.plugin.network

import net.corda.crypto.core.ShortHash
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.VNode
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.sdk.data.RequestId
import net.corda.sdk.network.RegistrationsLookup
import net.corda.sdk.network.VirtualNode
import net.corda.v5.base.types.MemberX500Name
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NetworkTasksImpl(var pc: ProjectContext) {

    /**
     * Creates vnodes specified in the config if they don't already exist.
     * @param [requiredNodes] Represents the list of VNodes as specified in the network Config json file (static-network-config.json)
     */
    fun createVNodes(
        requiredNodes: List<VNode>
    ) {
        val existingNodes = VirtualNode(pc.restClient).getAllVirtualNodes().virtualNodes

        // Check if each required vnode already exist, if not create it.
        val nodesToCreate = requiredNodes.filter { vn ->
            !existingNodes.any { en ->
                en.holdingIdentity.x500Name == vn.x500Name && en.cpiIdentifier.cpiName == vn.cpi
            }
        }

        nodesToCreate.forEach {
            val cpiUploadFilePath = if (it == pc.networkConfig.getMgmNode()) {
                pc.mgmCorDappCpiChecksumFilePath
            } else if (it.serviceX500Name == null) {
                pc.corDappCpiChecksumFilePath
            } else {
                pc.notaryCpiChecksumFilePath
            }
            VNodeHelper().createVNode(
                restClient = pc.restClient,
                vNode = it,
                cpiUploadStatusFilePath = cpiUploadFilePath
            )
        }

        nodesToCreate.forEach {
            // Check if the virtual node has been created.
            VirtualNode(pc.restClient).waitForX500NameToAppearInListOfAllVirtualNodes(
                x500Name = MemberX500Name.parse(it.x500Name!!),
                wait = 30.seconds
            )
            pc.logger.quiet("Virtual node for ${it.x500Name} is ready to be registered.")
        }
    }

    /**
     * Checks if the required virtual nodes have been registered and if not registers them.
     * @param [requiredNodes] Represents the list of VNodes as specified in the network Config json file (static-network-config.json)
     */
    fun registerVNodes(requiredNodes: List<VNode>) {
        val existingNodes = VirtualNode(pc.restClient).getAllVirtualNodes().virtualNodes
        val helper = VNodeHelper()
        val listOfDetails: MutableList<Triple<MemberX500Name, ShortHash, RegistrationRequestProgress>> = mutableListOf()

        requiredNodes.forEach { vn ->
            val match = helper.findMatchingVNodeFromList(existingNodes, vn)

            val shortHash = try {
                ShortHash.parse(match.holdingIdentity.shortHash)
            } catch (e: Exception) {
                throw CordaRuntimeGradlePluginException("Cannot read ShortHash for virtual node '${vn.x500Name}'")
            }

            if (!RegistrationsLookup(pc.restClient).isVnodeRegistrationApproved(
                    holdingIdentityShortHash = shortHash
                )
            ) {
                val regRequest = helper.getRegistrationRequest(
                    restClient = pc.restClient,
                    vNode = vn,
                    holdingId = shortHash,
                    clusterURI = URI.create(pc.cordaClusterURL),
                    isDynamicNetwork = pc.networkConfig.mgmNodeIsPresentInNetworkDefinition,
                    certificateAuthorityFilePath = pc.certificateAuthorityFilePath
                )
                val registration = helper.registerVNode(
                    restClient = pc.restClient,
                    registrationRequest = regRequest,
                    shortHash = shortHash
                )
                val detail = Triple(MemberX500Name.parse(vn.x500Name!!), shortHash, registration)
                listOfDetails.add(detail)
                pc.logger.quiet(
                    "Registering vNode: ${vn.x500Name} with shortHash: $shortHash. Registration request id: ${registration.registrationId}"
                )
            }
        }

        listOfDetails.forEach { vn ->
            val x500Name = vn.first
            val shortHash = vn.second
            val registrationId = vn.third.registrationId

            // Wait until the VNode is registered
            // The timeout is controlled by setting the vnodeRegistrationTimeout property
            RegistrationsLookup(pc.restClient).waitForRegistrationApproval(
                registrationId = RequestId(registrationId),
                holdingId = shortHash,
                wait = pc.vnodeRegistrationTimeout.milliseconds
            )
            pc.logger.quiet("VNode $x500Name with shortHash $shortHash registered.")
        }
    }
}