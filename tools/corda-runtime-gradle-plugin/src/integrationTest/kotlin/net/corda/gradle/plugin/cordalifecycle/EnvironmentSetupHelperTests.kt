package net.corda.gradle.plugin.cordalifecycle

import net.corda.gradle.plugin.configuration.NetworkConfig
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.test.assertTrue
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText

class EnvironmentSetupHelperTests {
    @TempDir
    lateinit var tempDir: Path

    private val publicVersion = "5.1.0.0"
    private val hcVersion = "5.1.0.0-HC15"
    private val rcVersion = "5.1.0.0-RC03"
    private val alphaVersion = "5.2.0.0-alpha-1706270718014"
    private val betaVersion = "5.2.0.0-beta-1706271586528"
    private val cordaArtifactoryUsername = System.getProperty("cordaArtifactoryUsername")
    private val cordaArtifactoryPassword = System.getProperty("cordaArtifactoryPassword")

    @Test
    fun downloadNotaryServerCpbFromGitHub() {
        val version = publicVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadNotaryCpb(
            version,
            targetFile.path,
            "",
            ""
        )
        assertTrue(targetFile.exists(), "The notary server cpb file should have been downloaded from GitHub")
    }

    @Test
    fun downloadNotaryServerCpbHC() {
        val version = hcVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadNotaryCpb(
            version,
            targetFile.path,
            cordaArtifactoryUsername,
            cordaArtifactoryPassword
        )
        assertTrue(targetFile.exists(), "The HC notary server cpb file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadNotaryServerCpbRC() {
        val version = rcVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadNotaryCpb(
            version,
            targetFile.path,
            cordaArtifactoryUsername,
            cordaArtifactoryPassword
        )
        assertTrue(targetFile.exists(), "The RC notary server cpb file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadNotaryServerCpbAlpha() {
        val version = alphaVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadNotaryCpb(
            version,
            targetFile.path,
            cordaArtifactoryUsername,
            cordaArtifactoryPassword
        )
        assertTrue(targetFile.exists(), "The alpha notary server cpb file should have been downloaded from Artifactory")
    }

    @Test
    fun downloadNotaryServerCpbBeta() {
        val version = betaVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        EnvironmentSetupHelper().downloadNotaryCpb(
            version,
            targetFile.path,
            cordaArtifactoryUsername,
            cordaArtifactoryPassword
        )
        assertTrue(targetFile.exists(), "The beta notary server cpb file should have been downloaded from Artifactory")
    }

    @Test
    fun unableToDownloadUnpublishedNotaryServerCpbWithoutCredentials() {
        val version = betaVersion
        val fileName = "notary-plugin-non-validating-server-$version-package.cpb"
        val targetFile = File("$tempDir/$fileName")
        val exception = assertThrows<CordaRuntimeGradlePluginException> {
            EnvironmentSetupHelper().downloadNotaryCpb(
                version,
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

    @Test
    fun `when no protocol name is specified we default to nonvalidating notary`() {
        val configFile = createTempFile()
        configFile.writeText(
            """
                [
                    {
                        "x500Name" : "CN=NotaryRep1, OU=Test Dept, O=R3, L=London, C=GB",
                        "cpi" : "NotaryServer",
                        "serviceX500Name": "CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB"
                    }
                ]
            """.trimIndent()
        )

        val networkConfig = NetworkConfig(configFile.absolutePathString())
        val result = EnvironmentSetupHelper().isNotaryNonValidating(networkConfig)
        assertThat(result).isTrue
    }

    @Test
    fun `when protocol contains 'nonvalid', we use nonvalidating notary`() {
        val configFile = createTempFile()
        configFile.writeText(
            """
                [
                    {
                        "x500Name" : "CN=NotaryRep1, OU=Test Dept, O=R3, L=London, C=GB",
                        "cpi" : "NotaryServer",
                        "serviceX500Name": "CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB",
                        "flowProtocolName" : "com.r3.corda.notary.plugin.nonvalidating"
                    }
                ]
            """.trimIndent()
        )

        val networkConfig = NetworkConfig(configFile.absolutePathString())
        val result = EnvironmentSetupHelper().isNotaryNonValidating(networkConfig)
        assertThat(result).isTrue
    }

    @Test
    fun `when protocol does not contain nonvalid but specifies contractverifying, we use contract verifying notary`() {
        val configFile = createTempFile()
        configFile.writeText(
            """
                [
                    {
                        "x500Name" : "CN=NotaryRep1, OU=Test Dept, O=R3, L=London, C=GB",
                        "cpi" : "NotaryServer",
                        "serviceX500Name": "CN=NotaryService, OU=Test Dept, O=R3, L=London, C=GB",
                        "flowProtocolName" : "com.r3.corda.notary.plugin.contractverifying"
                    }
                ]
            """.trimIndent()
        )

        val networkConfig = NetworkConfig(configFile.absolutePathString())
        val result = EnvironmentSetupHelper().isNotaryNonValidating(networkConfig)
        assertThat(result).isFalse
    }
}