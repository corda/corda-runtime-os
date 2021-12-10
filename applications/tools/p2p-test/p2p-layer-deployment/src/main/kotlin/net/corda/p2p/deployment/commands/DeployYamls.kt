package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.Yaml

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
        ProcessRunner.runWithInputs(
            listOf(
                "kubectl",
                "apply",
                "-f",
                "-"
            ),
            rawYamls.toByteArray()
        )
    }
}
