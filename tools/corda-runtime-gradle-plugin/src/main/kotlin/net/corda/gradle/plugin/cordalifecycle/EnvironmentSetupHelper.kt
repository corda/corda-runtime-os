package net.corda.gradle.plugin.cordalifecycle

import net.corda.sdk.network.config.NetworkConfig
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.gradle.plugin.retryAttempts
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.ConfigSchemaVersion
import net.corda.restclient.generated.models.UpdateConfigParameters
import net.corda.schema.configuration.ConfigKeys.RootConfigKey
import net.corda.sdk.config.ClusterConfig
import java.io.File
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class EnvironmentSetupHelper {

    fun isNotaryNonValidating(networkConfig: NetworkConfig): Boolean {
        val notaryNodes = networkConfig.vNodes.filter { it.serviceX500Name != null }
        val nodesWithProtocolName = notaryNodes.filter { it.flowProtocolName != null }
        if (notaryNodes.isEmpty() || nodesWithProtocolName.isEmpty()) {
            // revert to default behaviour
            return true
        }

        return nodesWithProtocolName.any { it.flowProtocolName!!.lowercase().contains("nonvalid") }
    }

    fun downloadNotaryCpb(
        notaryCpbVersion: String,
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
        } else {
            val cordaReleaseVersion = "release-$notaryCpbVersion"
            URL(
                "https://github.com/corda/corda-runtime-os/releases/download/$cordaReleaseVersion/" +
                        "notary-plugin-non-validating-server-$notaryCpbVersion-package.cpb"
            )
        }
        if (!File(targetFilePath).exists()) {
            File(targetFilePath).parentFile.mkdirs()
            retryAttempts(attempts = 10) {
                url.openStream().use { Files.copy(it, Paths.get(targetFilePath), StandardCopyOption.REPLACE_EXISTING) }
            }
        }
    }

    fun getConfigVersion(
        restClient: CordaRestClient,
        configSection: RootConfigKey,
    ): Int {
        return ClusterConfig(restClient).getCurrentConfig(configSection).version
    }

    @Suppress("LongParameterList")
    fun sendUpdate(
        restClient: CordaRestClient,
        configSection: RootConfigKey,
        configBody: String,
        configVersion: Int
    ) {
        val updateConfigParameters = UpdateConfigParameters(
            configBody,
            ConfigSchemaVersion(1, 0),
            configSection.value,
            configVersion
        )
        try {
            ClusterConfig(restClient).updateConfig(updateConfigParameters)
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Failed to Update Config", e)
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
