package net.corda.p2p.deployment.commands.simulator.db

import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "status",
    showDefaultValues = true,
    description = ["Check if database is running"]
)
class DbStatus : Runnable {
    @Option(
        names = ["-n", "--name"],
        description = ["The name of the database namespace"],
    )
    private var name = Db.defaultName

    override fun run() {
        val status = Db.getDbStatus(name)
        if (status == null) {
            println("DB in $name is not running")
        } else {
            println("DB in $name is ${status.status}:")
            println("\t username: ${status.username}")
            println("\t password: ${status.password}")
        }
    }
}
