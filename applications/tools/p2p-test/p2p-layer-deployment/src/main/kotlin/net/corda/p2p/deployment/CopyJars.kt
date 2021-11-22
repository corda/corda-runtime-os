package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "copyJars",
    description = ["Copy jars into pods"]
)
// YIFT: Remove this
class CopyJars : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    private var namespaceName = "p2p-layer"

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val getPods = ProcessBuilder().command(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "-o", "yaml",
        ).start()
        if (getPods.waitFor() != 0) {
            System.err.println(getPods.errorStream.reader().readText())
            throw RuntimeException("Could not get pods")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getPods.inputStream, Map::class.java)
        val pods = rawData["items"] as List<Yaml>
        pods.map {
            val spec = it["spec"] as Yaml
            val containers = spec["containers"] as List<Yaml>
            val app = containers.firstOrNull()?.get("name")
            val metadata = it["metadata"] as Yaml
            val name = metadata["name"] as? String
            name to app as? String
        }.mapNotNull { (name, app) ->
            if (app == "configurator") {
                name to "./applications/tools/p2p-test/configuration-publisher/build/bin/"
            } else if (app?.startsWith("gateway-") == true) {
                name to "./applications/p2p-gateway/build/bin/"
            } else if (app?.startsWith("link-manager-") == true) {
                name to "./applications/p2p-link-manager/build/bin/"
            } else {
                null
            }
        }.onEach { (name, _) ->
            val mkDir = ProcessBuilder().command(
                "kubectl",
                "exec",
                "-n", namespaceName,
                name,
                "--",
                "mkdir", "-p", "/opt/override/jars"
            ).inheritIO()
                .start()
            mkDir.waitFor()
        }.onEach { (name, dir) ->
            val cp = ProcessBuilder().command(
                "kubectl",
                "cp",
                dir,
                "$namespaceName/$name:/opt/override/jars",
            ).inheritIO()
                .start()
            cp.waitFor()
        }
    }
}
