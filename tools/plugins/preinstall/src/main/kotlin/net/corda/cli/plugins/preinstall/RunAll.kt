package net.corda.cli.plugins.preinstall

import picocli.CommandLine
import java.util.concurrent.Callable

@CommandLine.Command(name = "run-all", description = ["Runs all preinstall checks."])
class RunAll : Callable<Int> {

    @CommandLine.Parameters(
        index = "0",
        description = ["YAML file containing all configurations"]
    )
    lateinit var path: String

    @CommandLine.Option(
        names = ["-n", "--namespace"],
        description = ["The namespace in which to look for both the Postgres and Kafka secrets"]
    )
    var namespace: String? = null

    @CommandLine.Option(
        names = ["-f", "--file"],
        description = ["The file location of the truststore for Kafka"]
    )
    var truststoreLocation: String? = null

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
        val limitsArgs = mutableListOf<String>()

        val postgresCMD = CheckPostgres()
        val postgresArgs = mutableListOf<String>()
        namespace?.let{ postgresArgs.add("-n$it") }

        val kafkaCMD = CheckKafka()
        val kafkaArgs = mutableListOf<String>()
        namespace?.let{ kafkaArgs.add("-n$it") }
        truststoreLocation?.let{ kafkaArgs.add("-f$it") }
        kafkaArgs.add("-t$timeout")

        CommandLine(limitsCMD).execute(path, *limitsArgs.toTypedArray())
        CommandLine(postgresCMD).execute(path, *postgresArgs.toTypedArray())
        CommandLine(kafkaCMD).execute(path, *kafkaArgs.toTypedArray())

        report.addEntries(limitsCMD.report)
        report.addEntries(postgresCMD.report)
        report.addEntries(kafkaCMD.report)

        println(report)

        return report.testsPassed()
    }

}