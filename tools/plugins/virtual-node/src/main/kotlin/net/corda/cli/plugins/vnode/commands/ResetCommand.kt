package net.corda.cli.plugins.vnode.commands

import net.corda.cli.api.serviceUsers.HttpServiceUser
import net.corda.cli.api.services.HttpService
import picocli.CommandLine.Mixin
import picocli.CommandLine.Command
import picocli.CommandLine.Option


@Command(
    name = "reset",
    description = ["to do"]
)
class ResetCommand : Runnable, HttpServiceUser {

    @Mixin
    override lateinit var service: HttpService

    @Option(names = ["--cpi", "-c"], required = true, description = ["CPI file to reset the virtual node with."])
    lateinit var cpiFileName: String

    override fun run() {
        TODO("Not yet implemented")
    }
}