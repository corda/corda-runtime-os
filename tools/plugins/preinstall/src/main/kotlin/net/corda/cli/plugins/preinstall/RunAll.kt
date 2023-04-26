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
        names = ["-u", "--url"],
        description = ["The kubernetes cluster URL (if the preinstall is being called from outside the cluster)"]
    )
    var url: String? = null

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

    @CommandLine.Option(
        names = ["-m", "--max-idle"],
        description = ["The maximum ms a connection can be idle for while testing the kafka connection - defaults to 5000"]
    )
    var maxIdleMs: Int = 5000

    @CommandLine.Option(
        names = ["-v", "--verbose"],
        description = ["Display additional information about the configuration provided"]
    )
    var verbose: Boolean = false

    @CommandLine.Option(
        names = ["-d", "--debug"],
        description = ["Show information for debugging purposes"]
    )
    var debug: Boolean = false

    // Suppress Detekt's spread operator warning. The array copy here is minor and so performance decrease is negligible.
    @SuppressWarnings("SpreadOperator")
    override fun call(): Int {
        val report = PreInstallPlugin.Report()

        val limitsCMD = CheckLimits()
        val limitsArgs = mutableListOf<String>()
        if (verbose) { limitsArgs.add("-v") }
        if (debug) { limitsArgs.add("-d") }

        val postgresCMD = CheckPostgres()
        val postgresArgs = mutableListOf<String>()
        if (verbose) { postgresArgs.add("-v") }
        if (debug) { postgresArgs.add("-d") }
        namespace?.let{ postgresArgs.add("-n$it") }
        url?.let{ postgresArgs.add("-n$it") }

        val kafkaCMD = CheckKafka()
        val kafkaArgs = mutableListOf<String>()
        if (verbose) { kafkaArgs.add("-v") }
        if (debug) { kafkaArgs.add("-d") }
        namespace?.let{ kafkaArgs.add("-n$it") }
        url?.let{ kafkaArgs.add("-n$it") }
        truststoreLocation?.let{ kafkaArgs.add("-f$it") }
        kafkaArgs.add("-t$timeout")
        kafkaArgs.add("-m$maxIdleMs")

        CommandLine(limitsCMD).execute(path, *limitsArgs.toTypedArray())
        CommandLine(postgresCMD).execute(path, *postgresArgs.toTypedArray())
        CommandLine(kafkaCMD).execute(path, *kafkaArgs.toTypedArray())

        report.addEntries(limitsCMD.report)
        report.addEntries(postgresCMD.report)
        report.addEntries(kafkaCMD.report)

        return report.testsPassed()
    }

}