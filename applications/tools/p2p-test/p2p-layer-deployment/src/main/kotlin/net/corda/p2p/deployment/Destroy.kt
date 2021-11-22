package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "destroy",
    description = ["Delete a running namespace"]
)
class Destroy : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    var namespaceName = "p2p-layer"

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        println("Removing namespace $namespaceName...")
        val delete = ProcessBuilder().command(
            "kubectl",
            "delete",
            "namespace",
            namespaceName
        ).inheritIO().start()
        delete.waitFor()

        println("Removing volumes from $namespaceName...")
        val getClaims = ProcessBuilder().command(
            "kubectl",
            "get",
            "pv",
            "-o", "yaml",
        ).start()
        if (getClaims.waitFor() != 0) {
            System.err.println(getClaims.errorStream.reader().readText())
            throw RuntimeException("Could not get volumes")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getClaims.inputStream, Map::class.java)
        val volumes = rawData["items"] as List<Yaml>
        volumes.map { volume ->
            val metadata = volume["metadata"] as Yaml
            val name = metadata["name"] as String
            val spec = volume["spec"] as Yaml
            val claimRef = spec["claimRef"] as? Yaml
            val namespace = claimRef?.get("namespace") as? String
            name to namespace
        }.filter {
            it.second == namespaceName
        }.map {
            it.first
        }.forEach {
            val deleteClaim = ProcessBuilder().command(
                "kubectl",
                "delete",
                "pv",
                it
            ).inheritIO().start()
            deleteClaim.waitFor()
        }
    }
}
