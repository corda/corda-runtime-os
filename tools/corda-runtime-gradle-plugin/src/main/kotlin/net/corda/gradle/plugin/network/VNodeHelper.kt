package net.corda.gradle.plugin.network

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import net.corda.gradle.plugin.dtos.RegistrationRequestProgressDTO
import net.corda.gradle.plugin.dtos.VNode
import net.corda.gradle.plugin.dtos.VirtualNodeInfoDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.retry
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.endpoints.v1.VirtualNodeRestResource
import net.corda.libs.virtualnode.endpoints.v1.types.CreateVirtualNodeRequestType
import net.corda.rest.client.RestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import java.io.File
import java.net.HttpURLConnection
import java.time.Duration
import java.util.*

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

    fun findMatchingVNodeFromList(existingNodes: List<VirtualNodeInfoDTO>, requiredNode: VNode): VirtualNodeInfoDTO {
        val matches = existingNodes.filter { en ->
            en.holdingIdentity?.x500Name.equals(requiredNode.x500Name) &&
                    en.cpiIdentifier?.cpiName.equals(requiredNode.cpi)
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
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        vNode: VNode,
        shortHash: String,
        vnodeRegistrationTimeout: Long
    ) {
        val registrationBody = if (vNode.serviceX500Name == null) {
            """ 
            { 
                "memberRegistrationRequest" : {
                    "context" : {
                        "corda.key.scheme" : "CORDA.ECDSA.SECP256R1" 
                    }
                }
            }
            """.trimIndent()
        } else {
            val flowProtocolValue = vNode.flowProtocolName ?: "com.r3.corda.notary.plugin.nonvalidating"
            val backchainValue = vNode.backchainRequired ?: "true"
            """
            { 
                "memberRegistrationRequest" : {
                    "context" : {
                        "corda.key.scheme" : "CORDA.ECDSA.SECP256R1",
                        "corda.roles.0" : "notary",
                        "corda.notary.service.name" : "${vNode.serviceX500Name}",
                        "corda.notary.service.flow.protocol.version.0" : "1",
                        "corda.notary.service.flow.protocol.name" : "$flowProtocolValue",
                        "corda.notary.service.backchain.required" : "$backchainValue"
                    } 
                }
            }
            """.trimIndent()
        }

        val response: HttpResponse<JsonNode> = Unirest.post("$cordaClusterURL/api/v1/membership/$shortHash")
            .body(registrationBody)
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()

        if (response.status != HttpURLConnection.HTTP_OK) {
            throw CordaRuntimeGradlePluginException(
                "Failed to request registration of virtual node $shortHash, response status: ${response.status}, reason: ${response.body}"
            )
        }

        // Wait until the VNode is registered
        // The timeout is controlled by setting the vnodeRegistrationTimeout property
        retry(timeout = Duration.ofMillis(vnodeRegistrationTimeout)) {
            if (!checkVNodeIsRegistered(
                    cordaClusterURL,
                    cordaRestUser,
                    cordaRestPassword,
                    shortHash
                )
            ) {
                throw CordaRuntimeGradlePluginException("VNode $shortHash not registered.")
            }
        }
    }

    /**
     * Checks if a virtual node with given shortHash has been registered
     * @return returns true if the vnode is registered
     */
    fun checkVNodeIsRegistered(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        shortHash: String
    ): Boolean {

        val response: HttpResponse<JsonNode> = Unirest.get("$cordaClusterURL/api/v1/membership/$shortHash")
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()

        if (response.status != HttpURLConnection.HTTP_OK) {
            return false
        }

        try {
            if (!response.body.array.isEmpty) {
                val requests: List<RegistrationRequestProgressDTO> = mapper.readValue(
                    response.body.toString(),
                    object : TypeReference<List<RegistrationRequestProgressDTO>>() {})

                // Returns true if any requests have registrationStatus of "APPROVED"
                return requests.any { request ->
                    Objects.equals(request.registrationStatus, "APPROVED")
                }
            }
            // Returns false if array was empty or "APPROVED" wasn't found
            return false
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to check registration status for $shortHash with exception: ${e.message}.", e)
        }
    }
}