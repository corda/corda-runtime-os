package net.corda.p2p.deployment

import net.corda.p2p.deployment.commands.Bash
import net.corda.p2p.deployment.commands.CreateStores
import net.corda.p2p.deployment.commands.DbSink
import net.corda.p2p.deployment.commands.Destroy
import net.corda.p2p.deployment.commands.Jdbc
import net.corda.p2p.deployment.commands.Log
import net.corda.p2p.deployment.commands.Psql
import net.corda.p2p.deployment.commands.Receive
import net.corda.p2p.deployment.commands.Send
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
        Receive::class,
        DbSink::class,
        Send::class,
        CreateStores::class,
    ],
    header = ["Deployer for p2p layer"],
    name = "deployer",
)
class Go
@Suppress("SpreadOperator")
fun main(args: Array<String>): Unit =
    exitProcess(CommandLine(Go()).execute(*args))
