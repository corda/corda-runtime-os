package net.corda.cli.commands.dbconfig

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine

@CommandLine.Command(
    name = "database",
    subcommands = [Spec::class],
    mixinStandardHelpOptions = true,
    description = ["Does Database bootstrapping and upgrade"],
)
class DatabaseBootstrapAndUpgradeCommand {
    companion object {
        val classLoader: ClassLoader = this::class.java.classLoader
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
