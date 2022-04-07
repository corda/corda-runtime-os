package net.corda.cli.plugin.initialconfig

import picocli.CommandLine
import java.io.File
import java.io.FileWriter

@Suppress("Unused")
@CommandLine.Command(
    name = "create-user-config",
    description = ["Create the SQL script for adding the RBAC configuration for an initial admin user"]
)
class RbacConfigSubcommand : Runnable {
    @CommandLine.Option(
        names = ["-u", "--user"],
        description = ["User name for the initial admin user"]
    )
    var user: String? = null

    @CommandLine.Option(
        names = ["-p", "--password"],
        description = ["Password for the initial admin user. Leave out for SSO"]
    )
    var password: String? = null

    @CommandLine.Option(
        names = ["-l", "--location"],
        description = ["location to write the sql output to"]
    )
    var location: String? = null

    @CommandLine.Spec
    lateinit var spec: CommandLine.Model.CommandSpec

    override fun run() {
        if (user.isNullOrEmpty()) {
            throw CommandLine.ParameterException(spec.commandLine(), "A user id must be specified.")
        }

        val output = buildRbacConfigSql(user!!, password, "Setup Script")

        if (location == null) {
            println(output)
        } else {
            FileWriter(File("${location!!.removeSuffix("/")}/rbac-config.sql")).run {
                write(output)
                flush()
                close()
            }
        }
    }

    @Suppress("Unused")
    @CommandLine.Command(
        name = "create-dev-config",
        description = ["Create a config with standard admin user/password and self-signed cert"]
    )
    fun createDevConfig() {
        if (user == null) {
            user = "admin"
        }

        if (password == null) {
            password = "admin"
        }
        run()
    }

}