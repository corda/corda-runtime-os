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
    ){
        // Get Cpb
        val cpbFile = File(cpbFilePath)
        if (!cpbFile.exists()) {
            throw CordaRuntimeGradlePluginException("Cpb file not found at: $cpbFilePath.")
        }
        // Clear previous cpi if it exists
        val cpiFile = File(cpiFilePath)
        if (cpiFile.exists()) {
            cpiFile.delete()
        }
        // Build and execute command to build cpi
        val cmdList = mutableListOf<String>()

        cmdList.add("$javaBinDir/java")
        cmdList.add("-Dpf4j.pluginsDir=$cordaCliBinDir/plugins/")
        cmdList.add("-jar")
        cmdList.add("$cordaCliBinDir/corda-cli.jar")
        cmdList.add("package")
        cmdList.add("create-cpi")
        cmdList.add("--cpb")
        cmdList.add(cpbFile.absolutePath)
        cmdList.add("--group-policy")
        cmdList.add(groupPolicyFilePath)
        cmdList.add("--cpi-name")
        cmdList.add(cpiName)
        cmdList.add("--cpi-version")
        cmdList.add(cpiVersion )
        cmdList.add("--file")
        cmdList.add(cpiFilePath)
        cmdList.add("--keystore")
        cmdList.add(keystoreFilePath)
        cmdList.add("--storepass")
        cmdList.add(keystorePassword)
        cmdList.add("--key")
        cmdList.add(keystoreAlias)

        val pb = ProcessBuilder(cmdList)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.inputStream.transferTo(System.out)
        proc.waitFor()
    }
}