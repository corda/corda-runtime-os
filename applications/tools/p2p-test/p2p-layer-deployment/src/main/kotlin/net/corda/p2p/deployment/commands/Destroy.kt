package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.p2p.deployment.DeploymentException
import net.corda.p2p.deployment.Yaml
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "destroy",
    description = ["Delete a running namespace"]
)
class Destroy : Runnable {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun destroy(namespaceName: String) {
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
                throw DeploymentException("Could not get volumes")
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
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    var namespaceName = "p2p-layer"

    @Option(
        names = ["--all"],
        description = ["Destroy all the namespaces"]
    )
    private var all = false

    @Suppress("UNCHECKED_CAST")
    private fun getNamespaces(): Collection<String> {
        val getAll = ProcessBuilder().command(
            "kubectl",
            "get",
            "namespace",
            "-o",
            "yaml"
        ).start()
        if (getAll.waitFor() != 0) {
            System.err.println(getAll.errorStream.reader().readText())
            throw DeploymentException("Could not get namespaces")
        }
        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getAll.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        return items.map {
            it["metadata"] as Yaml
        }.filter {
            val annotations = it["annotations"] as? Yaml
            annotations?.get("type") == "p2p"
        }.mapNotNull {
            it["name"] as? String
        }
    }

    override fun run() {
        if (all) {
            getNamespaces().forEach {
                destroy(it)
            }
        } else {
            destroy(namespaceName)
        }
    }
}
