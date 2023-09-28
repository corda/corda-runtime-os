package net.corda.cli.plugins.preinstall

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
        val report = PreInstallPlugin.Report()

        val limitsCMD = CheckLimits()
        limitsCMD.path = path

        val postgresCMD = CheckPostgres()
        postgresCMD.path = path
        namespace?.let{ postgresCMD.namespace = it }

        val kafkaCMD = CheckKafka()
        kafkaCMD.path = path
        namespace?.let{ kafkaCMD.namespace = it }
        kafkaCMD.timeout = timeout

        limitsCMD.call()
        postgresCMD.call()
        kafkaCMD.call()

        report.addEntries(limitsCMD.report)
        report.addEntries(postgresCMD.report)
        report.addEntries(kafkaCMD.report)

        return if (report.testsPassed()) {
            0
        } else {
            1
        }
    }

}