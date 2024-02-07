package net.corda.gradle.plugin.queries

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import net.corda.gradle.plugin.configuration.ProjectContext
import net.corda.gradle.plugin.dtos.CpiMetadataDTO
import net.corda.gradle.plugin.dtos.GetCPIsResponseDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.getExistingNodes
import java.net.HttpURLConnection

/**
 * Provides the functionality to query the corda cluster used in the corda-runtime-plugin-queries group of tasks
 */
class QueryTasksImpl(val pc: ProjectContext) {

    init {
        Unirest.config().verifySsl(false)
    }

    /**
     * Lists out the vnodes to the console
     */
    fun listVNodes() {
        val vNodes= getExistingNodes(pc)
        val cpiNamePadding = 40
        val shortHashPadding = 30
        val x500NamePadding = 60
        val cpiNameTitle = "CPI Name".padEnd(cpiNamePadding)
        val shortHashTitle = "Holding identity short hash".padEnd(shortHashPadding)
        val x500NameTitle = "X500 Name".padEnd(x500NamePadding)
        pc.logger.quiet(cpiNameTitle + shortHashTitle + x500NameTitle)
        vNodes.forEach {
            val cpiName = it.cpiIdentifier?.cpiName?.padEnd(cpiNamePadding)
            val shortHash = it.holdingIdentity?.shortHash?.padEnd(shortHashPadding)
            val x500Name = it.holdingIdentity?.x500Name?.padEnd(x500NamePadding)
            pc.logger.quiet(cpiName + shortHash + x500Name)
        }
    }

    /**
     * Lists out the uploaded Cpis to the console
     */
    fun listCPIs() {
        val cpis = getUploadedCpis(pc)
        val cpiNamePadding = 40
        val cpiVersionPadding = 20
        val cpiChecksumPadding = 16
        val cpiNameTitle = "CpiName".padEnd(cpiNamePadding)
        val cpiVersionTitle = "CpiVersion".padEnd(cpiVersionPadding)
        val cpiChecksumTitle = "CpiFileCheckSum".padEnd(cpiChecksumPadding)
        pc.logger.quiet(cpiNameTitle + cpiVersionTitle + cpiChecksumTitle)
        cpis.forEach {
            val cpiName = it.id?.cpiName?.padEnd(cpiNamePadding)
            val cpiVersion = it.id?.cpiVersion?.padEnd(cpiVersionPadding)
            val cpiChecksum = it.cpiFileChecksum?.padEnd(cpiChecksumPadding)
            pc.logger.quiet(cpiName + cpiVersion + cpiChecksum)
        }
    }

    private fun getUploadedCpis(pc: ProjectContext): List<CpiMetadataDTO> {
        Unirest.config().verifySsl(false)
        val mapper = ObjectMapper()
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val response: HttpResponse<JsonNode> = Unirest.get(pc.cordaClusterURL + "/api/v1/cpi")
            .basicAuth(pc.cordaRestUser, pc.cordaRestPassword)
            .asJson()

        if (response.status != HttpURLConnection.HTTP_OK) {
            throw CordaRuntimeGradlePluginException("Failed to get Existing CPIs, response status: " + response.status)
        }

        return try {
            mapper.readValue(response.body.toString(), GetCPIsResponseDTO::class.java).cpis!!
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to get Existing CPIs with exception: ${e.message}", e)
        }
    }
}
