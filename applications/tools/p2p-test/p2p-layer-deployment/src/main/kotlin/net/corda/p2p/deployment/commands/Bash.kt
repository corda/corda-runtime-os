package net.corda.p2p.deployment.commands

import net.corda.p2p.deployment.DeploymentException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "bash",
    description = ["Bash into one of the pods"],
    showDefaultValues = true,
    mixinStandardHelpOptions = true,
)
class Bash : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the namespace"],
        required = true,
    )
    private lateinit var namespaceName: String

    @Option(
        names = ["-p", "--pod"],
        description = ["The name of the pod"]
    )
    private var pod = "p2p-gateway-1"

    @Parameters(
        description = ["The command (with parameters) to run (default to bash)"]
    )
    private var params = listOf("bash")

    @Suppress("UNCHECKED_CAST")
    override fun run() {
        val name = ProcessRunner.execute(
            "kubectl",
            "get",
            "pod",
            "-n", namespaceName,
            "-l", "app=$pod",
            "--output", "jsonpath={.items[*].metadata.name}",
        )
        if (name.isBlank()) {
            throw DeploymentException("Could not find $pod")
        }

        ProcessRunner.follow(
            listOf(
                "kubectl",
                "exec",
                "-it",
                "-n",
                namespaceName,
                name,
                "--",
            ) +
                params.ifEmpty {
                    listOf("bash")
                }
        )
    }
}
