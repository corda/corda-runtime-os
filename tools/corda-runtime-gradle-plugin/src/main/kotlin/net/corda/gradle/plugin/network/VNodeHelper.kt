package net.corda.gradle.plugin.network

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.Unirest
import net.corda.crypto.core.ShortHash
import net.corda.gradle.plugin.dtos.VNode
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType
import net.corda.libs.virtualnode.endpoints.v1.types.VirtualNodeInfo
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.membership.rest.v1.types.response.RegistrationRequestProgress
import net.corda.rest.client.RestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.network.RegistrationRequest
import net.corda.sdk.network.RegistrationRequester
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import java.io.File
import java.net.HttpURLConnection

class VNodeHelper {

    private val mapper = ObjectMapper()

    init {
        Unirest.config().verifySsl(false)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun createVNode(
        uploaderRestClient: RestClient<CpiUploadRestResource>,
        restClient: RestClient<VirtualNodeRestResource>,
        vNode: VNode,
        cpiUploadStatusFilePath: String
    ) {
        val cpiCheckSum = readCpiChecksumFromFile(cpiUploadStatusFilePath)
        if (!CpiUploader().cpiChecksumExists(restClient = uploaderRestClient, checksum = cpiCheckSum)) {
            throw CordaRuntimeGradlePluginException("CPI $cpiCheckSum not uploaded.")
        }

        val request = CreateVirtualNodeRequestType.JsonCreateVirtualNodeRequest(
            x500Name = vNode.x500Name!!,
            cpiFileChecksum = cpiCheckSum.value,
            vaultDdlConnection = null,
            vaultDmlConnection = null,
            cryptoDdlConnection = null,
            cryptoDmlConnection = null,
            uniquenessDdlConnection = null,
            uniquenessDmlConnection = null,
        )
        val response = VirtualNode().create(
            restClient = restClient,
            request = request
        )
        val responseStatusCode = response.responseCode.statusCode
        if (responseStatusCode != HttpURLConnection.HTTP_ACCEPTED) {
            throw CordaRuntimeGradlePluginException("Creation of virtual node failed with response status: $responseStatusCode")
        }
    }

    /**
     * Reads the latest CPI checksums from file.
     */
    fun readCpiChecksumFromFile(
        cpiChecksumFilePath: String
    ): Checksum {
        try {
            val fis = File(cpiChecksumFilePath)
            // Mapper won't parse directly into Checksum
            return Checksum(mapper.readValue(fis, String::class.java))
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to read CPI checksum from file, with error: $e")
        }
    }

    fun findMatchingVNodeFromList(existingNodes: List<VirtualNodeInfo>, requiredNode: VNode): VirtualNodeInfo {
        val matches = existingNodes.filter { en ->
            en.holdingIdentity.x500Name == requiredNode.x500Name &&
                    en.cpiIdentifier.cpiName == requiredNode.cpi
        }
        if (matches.isEmpty()) {
            throw CordaRuntimeGradlePluginException(
                "Registration failed because virtual node for '${requiredNode.x500Name}' not found."
            )
        } else if (matches.size > 1) {
            throw CordaRuntimeGradlePluginException(
                "Registration failed because more than one virtual node for '${requiredNode.x500Name}'"
            )
        }
        return matches.single()
    }

    /**
     * Registers an individual Vnode
     */
    @Suppress("LongParameterList")
    fun registerVNode(
        restClient: RestClient<MemberRegistrationRestResource>,
        vNode: VNode,
        shortHash: ShortHash
    ): RegistrationRequestProgress {
        val registrationBody = if (vNode.serviceX500Name == null) {
            RegistrationRequest().createStaticMemberRegistrationRequest()
        } else {
            val flowProtocolValue = vNode.flowProtocolName ?: "com.r3.corda.notary.plugin.nonvalidating"
            val backchainValue = vNode.backchainRequired ?: "true"
            RegistrationRequest().createStaticNotaryRegistrationRequest(
                notaryServiceName = vNode.serviceX500Name!!,
                notaryServiceProtocol = flowProtocolValue,
                isBackchainRequired = backchainValue.toBoolean()
            )
        }

        return RegistrationRequester().requestRegistration(
            restClient = restClient,
            memberRegistrationRequest = registrationBody,
            holdingId = shortHash
        )
    }
}