package net.corda.cli.plugins.preinstall

import picocli.CommandLine

@CommandLine.Command(name = "run-all", description = ["Runs all preinstall checks."])
class RunAll : Runnable {

    @CommandLine.Parameters(index = "0", description = ["The yaml file to parse."])
    lateinit var path: String

    @CommandLine.Option(names = ["-n", "--namespace"], description = ["The namespace in which to look for the secrets"])
    var namespace: String? = null

    @CommandLine.Option(names = ["-v", "--verbose"], description = ["Display additional information when checking resources"])
    var verbose: Boolean = false

    @CommandLine.Option(names = ["-d", "--debug"], description = ["Show information about limit calculation for debugging purposes"])
    var debug: Boolean = false

    // Suppress Detekt's spread operator warning. The array copy here is minor and so performance decrease is negligible.
    @SuppressWarnings("SpreadOperator")
    override fun run() {
        val limitsCMD = CommandLine(CheckLimits())
        val limitsArgs = mutableListOf<String>()
        if (verbose) { limitsArgs.add("-v") }
        if (debug) { limitsArgs.add("-d") }

        val postgresCMD = CommandLine(CheckPostgres())
        val postgresArgs = mutableListOf<String>()
        if (verbose) { postgresArgs.add("-v") }
        if (debug) { postgresArgs.add("-d") }
        namespace?.let{ postgresArgs.add("-n$namespace") }

        val kafkaCMD = CommandLine(CheckKafka())
        val kafkaArgs = mutableListOf<String>()
        if (verbose) { kafkaArgs.add("-v") }
        if (debug) { kafkaArgs.add("-d") }
        namespace?.let{ kafkaArgs.add("-n$namespace") }

        limitsCMD.execute(path, *limitsArgs.toTypedArray())
        postgresCMD.execute(path, *postgresArgs.toTypedArray())
        kafkaCMD.execute(path, *kafkaArgs.toTypedArray())
    }

}