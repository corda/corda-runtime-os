package net.corda.gradle.plugin.cordapp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.restclient.CordaRestClient
import net.corda.sdk.data.Checksum
import net.corda.sdk.data.RequestId
import net.corda.sdk.packaging.CpiUploader
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DeployCpiHelper {

    private val mapper = ObjectMapper()

    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    @Suppress("LongParameterList")
    fun uploadCpi(
        restClient: CordaRestClient,
        cpiFilePath: String,
        cpiName: String,
        cpiVersion: String,
        cpiChecksumFilePath: String,
        cpiUploadTimeout: Long
    ): Checksum {
        val cpiUploader = CpiUploader(restClient)
        val cpiFile = File(cpiFilePath)
        val requestId =
            try {
                cpiUploader.uploadCpiEvenIfExists(
                    cpi = cpiFile,
                    cpiName = cpiName,
                    cpiVersion = cpiVersion,
                    wait = cpiUploadTimeout.toDuration(DurationUnit.MILLISECONDS)
                ).id
            } catch (e: Exception) {
                throw CordaRuntimeGradlePluginException("Failed to request upload of CPI: '$cpiName'.")
            }

        val cpiChecksum = cpiUploader.cpiChecksum(
            uploadRequestId = RequestId(requestId),
            wait = cpiUploadTimeout.toDuration(DurationUnit.MILLISECONDS)
        )
        writeChecksumToFile(checksum = cpiChecksum, cpiChecksumFilePath = cpiChecksumFilePath)
        return cpiChecksum
    }

    fun writeChecksumToFile(
        checksum: Checksum,
        cpiChecksumFilePath: String
    ) {
        PrintStream(FileOutputStream(cpiChecksumFilePath)).print(mapper.writeValueAsString(checksum.value))
    }
}
