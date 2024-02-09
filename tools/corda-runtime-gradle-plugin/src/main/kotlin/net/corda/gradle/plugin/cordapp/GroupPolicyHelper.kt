package net.corda.gradle.plugin.cordapp

import java.io.File

class GroupPolicyHelper {

    fun createStaticGroupPolicy(
        targetPolicyFile: File,
        x500Names: List<String?>,
        javaBinDir: String,
        pluginsDir: String,
        cordaCliBinDir: String
    ) {
        val cmdList = mutableListOf(
            "$javaBinDir/java",
            "-Dpf4j.pluginsDir=$pluginsDir",
            "-jar",
            "$cordaCliBinDir/corda-cli.jar",
            "mgm",
            "groupPolicy",
            "--endpoint-protocol=1",
            "--endpoint=http://localhost:1080"
        )

        for (id in x500Names) {
            cmdList.add("--name")
            cmdList.add(id!!)
        }

        val pb = ProcessBuilder(cmdList)
        pb.redirectErrorStream(true)
        val proc = pb.start()

        proc.inputStream.use { input ->
            targetPolicyFile.outputStream().use {
                    output ->
                input.copyTo(output)
            }
        }
    }
}