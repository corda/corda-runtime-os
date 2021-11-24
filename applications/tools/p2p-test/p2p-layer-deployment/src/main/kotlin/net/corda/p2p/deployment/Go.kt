package net.corda.p2p.deployment

import picocli.CommandLine
import picocli.CommandLine.Command
import kotlin.system.exitProcess

@Command(
    subcommands = [
        Namespace::class,
        Destroy::class,
        Bash::class,
        Psql::class,
        Log::class,
        Jdbc::class,
    ],
    header = ["Deployer for p2p layer"],
    name = "deployer",
)
class Go
@Suppress("SpreadOperator")
fun main(args: Array<String>): Unit =
    exitProcess(CommandLine(Go()).execute(*args))
