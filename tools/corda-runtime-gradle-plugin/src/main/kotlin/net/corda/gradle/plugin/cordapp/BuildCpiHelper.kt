package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import net.corda.sdk.packaging.CpiAttributes
import net.corda.sdk.packaging.CpiV2Creator
import net.corda.sdk.packaging.signing.SigningOptions
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class BuildCpiHelper {

    @Suppress("LongParameterList")
    fun createCPI(
        groupPolicyFilePath: String,
        keystoreFilePath: String,
        keystoreAlias: String,
        keystorePassword: String,
        cpbFilePath: String,
        cpiFilePath: String,
        cpiName: String,
        cpiVersion: String,
    ) {
        // Get Cpb
        val cpbFile = Path.of(cpbFilePath).also {
            if (!it.exists()) throw CordaRuntimeGradlePluginException("CPB file not found at: $it.")
        }

        // Get GroupPolicy.json
        val groupPolicy = Path.of(groupPolicyFilePath).also {
            if (!it.exists()) throw CordaRuntimeGradlePluginException("Group Policy file not found at: $it.")
        }.readText(Charsets.UTF_8)

        // Get Keystore file
        val keyStoreFile = Path.of(keystoreFilePath).also {
            if (!it.exists()) throw CordaRuntimeGradlePluginException("Keystore file not found at: $it.")
        }

        // Clear previous cpi if it exists
        val cpiFile = Path.of(cpiFilePath).also { Files.deleteIfExists(it) }

        CpiV2Creator.createCpi(
            cpbFile,
            cpiFile,
            groupPolicy,
            CpiAttributes(cpiName, cpiVersion),
            SigningOptions(
                keyStoreFile,
                keystorePassword,
                keystoreAlias
            )
        )
    }
}