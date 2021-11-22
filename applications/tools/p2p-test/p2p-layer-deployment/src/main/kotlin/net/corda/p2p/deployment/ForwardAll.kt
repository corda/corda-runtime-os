package net.corda.p2p.deployment

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "forwardAll",
    description = ["Forward all the services to the local host"]
)
class ForwardAll : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"]
    )
    private var namespaceName = "p2p-layer"

    private val port = PortDetector()

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val getServices = ProcessBuilder().command(
            "kubectl",
            "get",
            "service",
            "-n", namespaceName,
            "-o", "yaml",
        ).start()
        if (getServices.waitFor() != 0) {
            System.err.println(getServices.errorStream.reader().readText())
            throw RuntimeException("Could not get services")
        }

        val reader = ObjectMapper(YAMLFactory()).reader()
        val rawData = reader.readValue(getServices.inputStream, Map::class.java)
        val items = rawData["items"] as List<Yaml>
        items.map { service ->
            val metadata = service["metadata"] as Yaml
            val name = metadata["name"] as? String
            println("$name:")
            val spec = service["spec"] as Yaml
            val ports = (spec["ports"] as Collection<Yaml>).map {
                it["name"] as String to port.next()
            }.onEach { (name, port) ->
                println("\t$name -> $port")
            }.map { (name, port) ->
                "$port:$name"
            }
            listOf(
                "kubectl",
                "port-forward",
                "-n",
                namespaceName,
                "service/$name"
            ) + ports
        }.map { commands ->
            ProcessBuilder(commands)
                .start()
        }.forEach {
            it.waitFor()
        }
    }
}
