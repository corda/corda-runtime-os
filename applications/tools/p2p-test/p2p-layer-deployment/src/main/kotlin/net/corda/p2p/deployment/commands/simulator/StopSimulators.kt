package net.corda.p2p.deployment.commands.simulator

import net.corda.p2p.deployment.commands.ProcessRunner

class StopSimulators(
    private val namespaceName: String,
    private val mode: String,
) : Runnable {
    override fun run() {
        GetSimulatorStatus(
            mode,
            namespaceName
        )().forEach {
            println("Stopping ${it.name}")
            ProcessRunner.follow(
                "kubectl",
                "delete",
                "job",
                "-n", namespaceName,
                it.name
            )
        }
    }
}
