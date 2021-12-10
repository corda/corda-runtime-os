package net.corda.p2p.deployment.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "destroy",
    showDefaultValues = true,
    description = ["Delete a running namespace"],
    mixinStandardHelpOptions = true,
)
class Destroy : Runnable {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun destroy(namespaceName: String) {
            println("Removing namespace $namespaceName...")
            ProcessRunner.follow(
                "kubectl",
                "delete",
                "namespace",
                namespaceName
            )
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
        val all = ProcessRunner.execute(
            "kubectl",
            "get",
            "namespace",
            "-l",
            "namespace-type=p2p-deployment,creator=${MyUserName.userName}",
            "-o",
            "jsonpath={.items[*].metadata.name}",
        )
        return all
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
