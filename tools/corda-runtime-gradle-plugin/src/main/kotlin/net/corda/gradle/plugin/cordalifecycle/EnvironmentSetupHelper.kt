package net.corda.gradle.plugin.cordalifecycle

import kong.unirest.Unirest
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths

class EnvironmentSetupHelper {

    @Suppress("LongParameterList")
    fun downloadCombinedWorker(
        combinedWorkerFileName: String,
        combinedWorkerVersion: String,
        cordaReleaseVersion: String,
        targetFilePath: String,
        artifactoryUsername: String,
        artifactoryPassword: String
    ) {
        val url = if (nameContainsRcOrHc(combinedWorkerFileName) || nameContainsAlphaOrBeta(combinedWorkerFileName)) {
            setupAuthentication(artifactoryUsername, artifactoryPassword)
            URL(
                "https://software.r3.com/artifactory/corda-os-maven/net/corda/" +
                        "corda-combined-worker/$combinedWorkerVersion/$combinedWorkerFileName"
            )
        } else URL("https://github.com/corda/corda-runtime-os/releases/download/$cordaReleaseVersion/$combinedWorkerFileName")
        if (!File(targetFilePath).exists()) {
            File(targetFilePath).parentFile.mkdirs()
            url.openStream().use { Files.copy(it, Paths.get(targetFilePath)) }
        }
    }

    fun downloadNotaryCpb(
        notaryCpbVersion: String,
        cordaReleaseVersion: String,
        targetFilePath: String,
        artifactoryUsername: String,
        artifactoryPassword: String
    ) {
        val url = if (nameContainsRcOrHc(notaryCpbVersion) || nameContainsAlphaOrBeta(notaryCpbVersion)) {
            setupAuthentication(artifactoryUsername, artifactoryPassword)
            URL(
                "https://software.r3.com/artifactory/corda-os-maven/com/r3/corda/notary/plugin/nonvalidating/" +
                        "notary-plugin-non-validating-server/$notaryCpbVersion/" +
                        "notary-plugin-non-validating-server-$notaryCpbVersion-package.cpb"
            )
        } else URL(
            "https://github.com/corda/corda-runtime-os/releases/download/$cordaReleaseVersion/" +
                    "notary-plugin-non-validating-server-$notaryCpbVersion-package.cpb"
        )
        if (!File(targetFilePath).exists()) {
            File(targetFilePath).parentFile.mkdirs()
            url.openStream().use { Files.copy(it, Paths.get(targetFilePath)) }
        }
    }

    fun getConfigVersion(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        configSection: String
    ): Int {
        return Unirest.get("$cordaClusterURL/api/v1/config/$configSection")
            .basicAuth(cordaRestUser, cordaRestPassword)
            .asJson()
            .ifSuccess {}.body.`object`["version"].toString().toInt()
    }

    @Suppress("LongParameterList")
    fun sendUpdate(
        cordaClusterURL: String,
        cordaRestUser: String,
        cordaRestPassword: String,
        configSection: String,
        configBody: String,
        configVersion: Int
    ) {
        Unirest.put("$cordaClusterURL/api/v1/config")
            .basicAuth(cordaRestUser, cordaRestPassword)
            .body(
                """
                {
                    "config": {
                        $configBody
                    },
                    "schemaVersion": {
                        "major": 1,
                        "minor": 0
                    },
                    "section": "$configSection",
                    "version": $configVersion
                }
                """.trimIndent()
            )
            .asJson()
            .ifFailure { response ->
                throw CordaRuntimeGradlePluginException("Failed to Update Config\n${response.body.`object`["title"]}")
            }
    }

    private fun nameContainsRcOrHc(combinedWorkerFileName: String): Boolean {
        return combinedWorkerFileName.contains("-RC") || combinedWorkerFileName.contains("-HC")
    }

    private fun nameContainsAlphaOrBeta(combinedWorkerFileName: String): Boolean {
        return combinedWorkerFileName.contains("alpha") || combinedWorkerFileName.contains("beta")
    }

    internal fun setupAuthentication(artifactoryUsername: String, artifactoryPassword: String) {
        if (artifactoryUsername.isBlank() || artifactoryPassword.isBlank()) {
            throw CordaRuntimeGradlePluginException(
                "Unpublished assets can only be pulled from R3's internal registry and require a username and password"
            )
        }
        MyAuthenticator.setPasswordAuthentication(artifactoryUsername, artifactoryPassword)
        Authenticator.setDefault(MyAuthenticator())
    }

    internal class MyAuthenticator : Authenticator() {
        protected override fun getPasswordAuthentication(): PasswordAuthentication {
            return PasswordAuthentication(
                username,
                password.toCharArray()
            )
        }

        companion object {
            private var username = ""
            private var password = ""
            fun setPasswordAuthentication(username: String, password: String) {
                Companion.username = username
                Companion.password = password
            }
        }
    }
}