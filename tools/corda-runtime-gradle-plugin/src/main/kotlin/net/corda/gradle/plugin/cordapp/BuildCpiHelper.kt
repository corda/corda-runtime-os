package net.corda.gradle.plugin.cordapp

import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import java.io.File

class BuildCpiHelper {

    @Suppress("LongParameterList")
    fun createCPI(
        javaBinDir: String,
        cordaCliBinDir: String,
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
        val cpbFile = File(cpbFilePath)
        if (!cpbFile.exists()) {
            throw CordaRuntimeGradlePluginException("CPB file not found at: $cpbFilePath.")
        }
        // Clear previous cpi if it exists
        val cpiFile = File(cpiFilePath)
        if (cpiFile.exists()) {
            cpiFile.delete()
        }
        // Build and execute command to build cpi
        val cmdList = listOf(
            "$javaBinDir/java",
            "-Dpf4j.pluginsDir=$cordaCliBinDir/plugins/",
            "-jar",
            "$cordaCliBinDir/corda-cli.jar",
            "package",
            "create-cpi",
            "--cpb",
            cpbFile.absolutePath,
            "--group-policy",
            groupPolicyFilePath,
            "--cpi-name",
            cpiName,
            "--cpi-version",
            cpiVersion,
            "--file",
            cpiFilePath,
            "--keystore",
            keystoreFilePath,
            "--storepass",
            keystorePassword,
            "--key",
            keystoreAlias
        )

        val pb = ProcessBuilder(cmdList)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.transferTo(System.out)
        proc.waitFor()
    }
}