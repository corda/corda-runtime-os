package net.corda.p2p.deployment.commands.simulator.db

import net.corda.p2p.deployment.commands.Destroy
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "stop",
    showDefaultValues = true,
    description = ["Stop a database"]
)
class StopDb : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the database namespace"],
    )
    private var name = Db.defaultName
    override fun run() {
        val delete = Destroy()
        delete.namespaceName = name
        delete.run()
    }
}
