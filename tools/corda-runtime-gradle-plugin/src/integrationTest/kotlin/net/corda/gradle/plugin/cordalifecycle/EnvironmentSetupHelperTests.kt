package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertTrue

class EnvironmentSetupHelperTests {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun downloadCombinedWorkerFromGitHub() {
        val version = "5.1.0.0"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadCombinedWorker(
            fileName,
            version,
            "release-$version",
            targetFile.path,
            "",
            ""
        )
        assertTrue(targetFile.exists(), "The combined worker file should have been downloaded from GitHub")
    }

    @Test
    fun downloadCombinedWorkerHC() {
        val username = System.getProperty("cordaArtifactoryUsername")
        val password = System.getProperty("cordaArtifactoryPassword")
        val version = "5.1.0.0-HC15"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadCombinedWorker(
            fileName,
            version,
            "release-$version",
            targetFile.path,
            username,
            password
        )
        assertTrue(targetFile.exists(), "The HC combined worker file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadCombinedWorkerRC() {
        val username = System.getProperty("cordaArtifactoryUsername")
        val password = System.getProperty("cordaArtifactoryPassword")
        val version = "5.1.0.0-RC03"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadCombinedWorker(
            fileName,
            version,
            "release-$version",
            targetFile.path,
            username,
            password
        )
        assertTrue(targetFile.exists(), "The RC combined worker file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadCombinedWorkerAlpha() {
        val username = System.getProperty("cordaArtifactoryUsername")
        val password = System.getProperty("cordaArtifactoryPassword")
        val version = "5.2.0.0-alpha-1706270718014"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadCombinedWorker(
            fileName,
            version,
            "release-$version",
            targetFile.path,
            username,
            password
        )
        assertTrue(targetFile.exists(), "The alpha combined worker file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadCombinedWorkerBeta() {
        val username = System.getProperty("cordaArtifactoryUsername")
        val password = System.getProperty("cordaArtifactoryPassword")
        val version = "5.2.0.0-beta-1706271586528"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadCombinedWorker(
            fileName,
            version,
            "release-$version",
            targetFile.path,
            username,
            password
        )
        assertTrue(targetFile.exists(), "The beta combined worker file should have been downloaded from Artifactory")
    }

    @Test
    fun unableToDownloadUnpublishedCombinedWorkerWithoutCredentials() {
        val version = "5.2.0.0-beta-1706271586528"
        val fileName = "corda-combined-worker-$version.jar"
        val targetFile = File("$tempDir/$fileName")
        val exception = assertThrows<CordaRuntimeGradlePluginException> {
            EnvironmentSetupHelper().downloadCombinedWorker(
                fileName,
                version,
                "release-$version",
                targetFile.path,
                "",
                ""
            )
        }
        assertTrue(
            exception.message!!.contains("require a username and password"),
            "We should not be able to download an unpublished version without credentials"
        )
    }
}