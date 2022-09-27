package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugins.common.HttpRpcCommand
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(
    name = "user-admin",
    description = ["""Creates a role ('UserAdminRole') which will permit: 
        - creation/deletion of users
        - creation/deletion of permissions
        - creation/deletion of roles
        - assigning/un-assigning roles to users
        - assigning/un-assigning permissions to roles"""]
)
class UserAdminSubcommand : HttpRpcCommand(), Runnable {

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun run() {
        logger.info("Running UserAdminSubcommand")
    }
}
