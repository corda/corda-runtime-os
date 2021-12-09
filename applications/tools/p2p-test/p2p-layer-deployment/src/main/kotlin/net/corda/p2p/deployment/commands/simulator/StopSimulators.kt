package net.corda.p2p.deployment.commands.simulator

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
            val kill = ProcessBuilder().command(
                "kubectl",
                "delete",
                "job",
                "-n", namespaceName,
                it.name
            ).inheritIO().start()
            kill.waitFor()
        }
    }
}
