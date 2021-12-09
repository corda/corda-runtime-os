package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import kotlin.concurrent.thread

open class DeployYamls(
    yamls: Collection<Yaml>,
) : Runnable {
    val rawYamls by lazy {
        val writer = ObjectMapper(YAMLFactory()).writer()
        yamls.joinToString("\n") {
            writer.writeValueAsString(it)
        }
    }
    override fun run() {
        val create = ProcessBuilder().command(
            "kubectl",
            "apply",
            "-f",
            "-"
        ).start()
        thread(isDaemon = true) {
            create.inputStream.reader().useLines {
                it.forEach { line ->
                    println(line)
                }
            }
        }
        thread(isDaemon = true) {
            create.errorStream.reader().useLines {
                it.forEach { line ->
                    System.err.println(line)
                }
            }
        }
        create.outputStream.write(rawYamls.toByteArray())
        create.outputStream.close()
        if (create.waitFor() != 0) {
            throw DeploymentException("Could not deploy")
        }
    }
}
