package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "destroy",
    showDefaultValues = true,
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
        }
    }
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
    )
    lateinit var namespaceName: String

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
            "-l",
            "namespace-type=p2p-deployment,creator=${MyUserName.userName}",
            "-o",
            "jsonpath={.items[*].metadata.name}",
        ).start()
        if (getAll.waitFor() != 0) {
            System.err.println(getAll.errorStream.reader().readText())
            throw DeploymentException("Could not get namespaces")
        }
        return getAll
            .inputStream
            .reader()
            .readText()
            .split(" ")
            .filter {
                it.isNotEmpty()
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
