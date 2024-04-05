package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import kong.unirest.Unirest
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRestResource
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DeployCpiHelper {

    private val mapper = ObjectMapper()

    init {
        Unirest.config().verifySsl(false)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Suppress("LongParameterList")
    fun uploadCpi(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        cpiFilePath: String,
        cpiName: String,
        cpiVersion: String,
        cpiCheksumFilePath: String,
        cpiUploadTimeout: Long
    ): String {

        val uploaderRestClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = true,
            username = cordaRestUser,
            password = cordaRestPassword,
            targetUrl = cordaClusterURL
        )
        val forceUploaderRestClient = RestClientUtils.createRestClient(
            VirtualNodeMaintenanceRestResource::class,
            insecure = true,
            username = cordaRestUser,
            password = cordaRestPassword,
            targetUrl = cordaClusterURL
        )
        val uploaderClass = CpiUploader()
        val cpiFile = File(cpiFilePath)
        val requestId =
            try {
                uploaderClass.uploadCpiEvenIfExists(
                    uploadRestClient = uploaderRestClient,
                    forceUploadRestClient = forceUploaderRestClient,
                    cpi = cpiFile.inputStream(),
                    cpiName = cpiName,
                    cpiVersion = cpiVersion,
                    wait = cpiUploadTimeout.toDuration(DurationUnit.MILLISECONDS)
                ).id
            } catch (e: Exception) {
                throw CordaRuntimeGradlePluginException("Failed to request upload of CPI: '$cpiName'.")
            }

        val cpiChecksum = uploaderClass.cpiChecksum(
            restClient = uploaderRestClient,
            uploadRequestId = requestId,
            wait = cpiUploadTimeout.toDuration(DurationUnit.MILLISECONDS)
        )
        PrintStream(FileOutputStream(cpiCheksumFilePath)).print(mapper.writeValueAsString(cpiChecksum))
        return cpiChecksum
    }
}