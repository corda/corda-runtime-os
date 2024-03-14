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
        try {
            val (cpbFile, groupPolicyFile, keyStoreFile) = requireFilesExist(cpbFilePath, groupPolicyFilePath, keystoreFilePath)
            val groupPolicy = groupPolicyFile.readText(Charsets.UTF_8)

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
        } catch (e: Exception) {
            throw CordaRuntimeGradlePluginException("Unable to create CPI: ${e.message}", e)
        }
    }

    private fun requireFilesExist(vararg filePath: String): List<Path> =
        filePath.map { path ->
            Path.of(path).also {
                if (!it.exists()) throw CordaRuntimeGradlePluginException("File not found: $it")
            }
        }
}