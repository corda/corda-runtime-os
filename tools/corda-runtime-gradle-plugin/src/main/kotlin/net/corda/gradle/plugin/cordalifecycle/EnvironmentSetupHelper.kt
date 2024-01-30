package net.corda.gradle.plugin.cordalifecycle

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

    private fun nameContainsRcOrHc(combinedWorkerFileName: String) : Boolean {
        return combinedWorkerFileName.contains("-RC") || combinedWorkerFileName.contains("-HC")
    }

    private fun nameContainsAlphaOrBeta(combinedWorkerFileName: String) : Boolean {
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