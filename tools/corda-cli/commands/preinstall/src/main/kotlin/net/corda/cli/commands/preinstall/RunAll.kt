package net.corda.cli.commands.preinstall

import net.corda.sdk.preinstall.checker.CompositeChecker
import net.corda.sdk.preinstall.checker.KafkaChecker
import net.corda.sdk.preinstall.checker.LimitsChecker
import net.corda.sdk.preinstall.checker.PostgresChecker
import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(name = "run-all", description = ["Runs all preinstall checks."], mixinStandardHelpOptions = true)
class RunAll : Callable<Int> {

    @CommandLine.Parameters(
        index = "0",
        description = ["YAML file containing all configurations"]
    )
    lateinit var path: String

    @CommandLine.Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for both the PostgreSQL and Kafka secrets"]
    )
    var namespace: String? = null

    @CommandLine.Option(
        names = ["-t", "--timeout"],
        description = ["The timeout in milliseconds for testing the kafka connection - defaults to 3000"]
    )
    var timeout: Int = 3000

    // Suppress Detekt's spread operator warning. The array copy here is minor and so performance decrease is negligible.
    @SuppressWarnings("SpreadOperator")
    override fun call(): Int {
        val checker = CompositeChecker().apply {
            addChecker(LimitsChecker(path))
            addChecker(PostgresChecker(path, namespace))
            addChecker(KafkaChecker(path, namespace, timeout))
        }

        return checker.check()
    }
}
