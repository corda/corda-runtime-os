package net.corda.cli.plugins.preinstall

import net.corda.sdk.preinstall.checker.PostgresChecker
import picocli.CommandLine
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "check-postgres",
    description = ["Check that the PostgreSQL DB is up and that the credentials work."],
    mixinStandardHelpOptions = true
)
class CheckPostgres : Callable<Int> {

    @Parameters(
        index = "0",
        description = ["YAML file containing the username and password values for PostgreSQL - either as values, or as secret references"]
    )
    lateinit var path: String

    @Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for the secrets if there are any"]
    )
    var namespace: String? = null

    override fun call(): Int = PostgresChecker(path, namespace).check()
}
