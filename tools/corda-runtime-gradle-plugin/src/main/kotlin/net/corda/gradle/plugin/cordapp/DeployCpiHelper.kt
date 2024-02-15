package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.HttpResponse
import kong.unirest.JsonNode
import kong.unirest.Unirest
import net.corda.gradle.plugin.dtos.CpiUploadResponseDTO
import net.corda.gradle.plugin.dtos.CpiUploadStatus
import net.corda.gradle.plugin.dtos.GetCPIsResponseDTO
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.retry
import net.corda.gradle.plugin.retryAttempts
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.HttpURLConnection
import java.time.Duration
import java.util.*

class DeployCpiHelper {

    private val mapper = ObjectMapper()

    init {
        Unirest.config().verifySsl(false)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }


    fun uploadCertificate(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        certAlias: String,
        certFilePath: String
    ) {

        val response = Unirest.put(cordaClusterURL + "/api/v1/certificates/cluster/code-signer")
            .field("alias", certAlias)
            .field("certificate", File(certFilePath))
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()

        // Note, api returns a 204 success code
        if (response.status != HttpURLConnection.HTTP_NO_CONTENT) {
            throw CordaRuntimeGradlePluginException(
                "Upload of certificate '$certAlias' failed with response status: ${response.status} and response body: ${response.body}."
            )
        }
    }

    @Suppress("LongParameterList")
    fun uploadCpi(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        cpiFilePath: String,
        cpiName: String,
        cpiVersion: String,
        cpiUploadStatusFilePath: String,
        cpiUploadTimeout: Long
    ): CpiUploadStatus {

        val response = if (cpiPreviouslyUploaded(
                    cordaClusterURL,
                    cordaRestUser,
                    cordaRestPassword,
                    cpiName,
                    cpiVersion
                )
            ) {
            Unirest.post("$cordaClusterURL/api/v1/maintenance/virtualnode/forcecpiupload/")
                .field("upload", File(cpiFilePath))
                .basicAuth(cordaRestUser, cordaRestPassword)
                .asJson()
        } else {
            Unirest.post(cordaClusterURL + "/api/v1/cpi/")
                .field("upload", File(cpiFilePath))
                .basicAuth(cordaRestUser, cordaRestPassword)
                .asJson()
        }
        if (response.status != HttpURLConnection.HTTP_OK) {
            throw CordaRuntimeGradlePluginException("Failed to request upload of CPI: '$cpiName'.")
        }

        val requestId = try {
            mapper.readValue(response.body.toString(), CpiUploadResponseDTO::class.java).id!!
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to request upload of CPI: '$cpiName'.", e)
        }

        val cpiUploadStatus = pollForCpiUpload(
            cordaClusterURL,
            cordaRestUser,
            cordaRestPassword,
            requestId,
            cpiUploadTimeout
        )
        PrintStream(FileOutputStream(cpiUploadStatusFilePath)).print(mapper.writeValueAsString(cpiUploadStatus))
        return cpiUploadStatus
    }

    /**
     * Checks if a CPI has previously been uploaded by comparing the cpiName and cpiVersion to CPIs already uploaded.
     */
    private fun cpiPreviouslyUploaded(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        cpiName: String, cpiVersion: String
    ): Boolean {

        val response: HttpResponse<JsonNode> = Unirest.get("$cordaClusterURL/api/v1/cpi")
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()

        if (response.status != HttpURLConnection.HTTP_OK) {
            throw CordaRuntimeGradlePluginException("Failed to check CPIs, response status:  ${response.status}.")
        }

        try {
            val cpisResponse: GetCPIsResponseDTO = mapper.readValue(response.body.toString(), GetCPIsResponseDTO::class.java)

            cpisResponse.cpis?.forEach { cpi ->
                if (Objects.equals(cpi.id!!.cpiName, cpiName) && Objects.equals(cpi.id!!.cpiVersion, cpiVersion)) return true
            }
            return false
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to check CPIs with exception: ${e.message}.", e)
        }
    }

    /**
     * Polls to see if a request to upload a CPI has been successful.
     * The timeout is controlled by setting the cpiUploadTimeout property
     */
    private fun pollForCpiUpload(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        id: String,
        cpiUploadTimeout: Long
    ): CpiUploadStatus {
        var cpiUploadStatus = CpiUploadStatus()
        retry(timeout = Duration.ofMillis(cpiUploadTimeout)) {
            cpiUploadStatus = checkCpiUploadStatus(
                cordaClusterURL,
                cordaRestUser,
                cordaRestPassword,
                id
            )
        }
        return cpiUploadStatus
    }

    /**
     * Check if a Cpi upload has been successful.
     */
    private fun checkCpiUploadStatus(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        id: String
    ): CpiUploadStatus {
        var cpiUploadStatus = CpiUploadStatus()
        Unirest.get(cordaClusterURL + "/api/v1/cpi/status/$id")
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()
            .ifSuccess { response ->
                cpiUploadStatus = mapper.readValue(response.body.toString(), CpiUploadStatus::class.java)
                if (cpiUploadStatus.status != "OK")
                    throw CordaRuntimeGradlePluginException(
                        "CPI Upload Status is not OK, current status:${cpiUploadStatus.status}."
                    )
            }
            .ifFailure(
                fun(response: HttpResponse<JsonNode>) {
                    throw CordaRuntimeGradlePluginException("Failed to get CPI Upload Status, response status: ${response.status}.")
                }
            )
        return cpiUploadStatus
    }
}