package net.corda.cli.application.commands

import net.corda.cli.application.services.Files
import org.yaml.snakeyaml.Yaml
import picocli.CommandLine
import java.io.FileInputStream
import java.util.concurrent.Callable

import java.io.PrintWriter




@CommandLine.Command(name = "set-node", description = ["Sets the current target for http requests."])
class SetCurrentNodeCommand : Callable<Int> {

    @CommandLine.Option(names = ["-n", "--node-url"], description = ["The Swagger Url of the target Node."], required = true)
    var url: String? = null

    private val yaml = Yaml()
    private val data: MutableMap<String, Any> = yaml.load(FileInputStream(Files.profile))

    override fun call(): Int {
        url?.let { data.put("url", it) }
        val writer = PrintWriter(Files.profile)
        yaml.dump(data, writer)
        return 0
    }
}
