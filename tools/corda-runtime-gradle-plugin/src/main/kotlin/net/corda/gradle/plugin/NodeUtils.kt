package net.corda.gradle.plugin

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.CpiMetadataDTO
import net.corda.gradle.plugin.dtos.GetCPIsResponseDTO
import net.corda.gradle.plugin.dtos.VirtualNodeInfoDTO
import net.corda.gradle.plugin.dtos.VirtualNodesDTO
import java.net.HttpURLConnection

/**
 * Gets a list of the virtual nodes which have already been created.
 * @return a list of the virtual nodes which have already been created.
 */
fun getExistingNodes(pc: ProjectContext) : List<VirtualNodeInfoDTO> {

    Unirest.config().verifySsl(false)
    val mapper = ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val response: HttpResponse<JsonNode> = Unirest.get(pc.cordaClusterURL + "/api/v1/virtualnode")
        .basicAuth(pc.cordaRpcUser, pc.cordaRpcPassword)
        .asJson()

    if (response.status != HttpURLConnection.HTTP_OK) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing vNodes, response status: " + response.status)
    }

    return try {
        mapper.readValue(response.body.toString(), VirtualNodesDTO::class.java).virtualNodes!!
    } catch (e: Exception) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing vNodes with exception: $e")
    }
}

fun getUploadedCpis(pc: ProjectContext): List<CpiMetadataDTO> {

    Unirest.config().verifySsl(false)
    val mapper = ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val response: HttpResponse<JsonNode> = Unirest.get(pc.cordaClusterURL + "/api/v1/cpi")
        .basicAuth(pc.cordaRpcUser, pc.cordaRpcPassword)
        .asJson()

    if (response.status != HttpURLConnection.HTTP_OK) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing Cpis, response status: " + response.status)
    }

    return try {
        mapper.readValue(response.body.toString(), GetCPIsResponseDTO::class.java).cpis!!
    } catch (e: Exception) {
        throw CordaRuntimeGradlePluginException("Failed to get Existing vNodes with exception: $e")
    }
}