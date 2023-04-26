package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.ResourceConfig
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ResourceValues
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Configurations
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import picocli.CommandLine
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option
import java.util.concurrent.Callable

@CommandLine.Command(name = "check-limits", description = ["Check the resource limits have been assigned correctly."])
class CheckLimits : Callable<Int>, PluginContext() {

    @Parameters(index = "0", description = ["YAML file containing resource limit overrides for the corda install"])
    lateinit var path: String

    @Option(names = ["-v", "--verbose"], description = ["Display additional information when checking resources"])
    var verbose: Boolean = false

    @Option(names = ["-d", "--debug"], description = ["Show information about limit calculation for debugging purposes"])
    var debug: Boolean = false

    // split resource into a digit portion and a unit portion
    private fun parseResourceString(resourceString: String): Long {
        val regex = Regex("(\\d+)([EPTGMK]i?[Bb]?)?")

        val (value, unit) = regex.matchEntire(resourceString)?.destructured
            ?: throw IllegalArgumentException("Invalid memory string format: $resourceString")

        // https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#setting-requests-and-limits-for-local-ephemeral-storage
        val multiplier = when (unit.uppercase()) {
            "E", "EB" -> 1024L * 1024L * 1024L * 1024L * 1024L * 1024L
            "P", "PB" -> 1024L * 1024L * 1024L * 1024L * 1024L
            "T", "TB" -> 1024L * 1024L * 1024L * 1024L
            "G", "GB" -> 1024L * 1024L * 1024L
            "M", "MB" -> 1024L * 1024L
            "K", "KB" -> 1024L
            "EI", "EIB" -> 1000L * 1000L * 1000L * 1000L * 1000L * 1000L
            "PI", "PIB" -> 1000L * 1000L * 1000L * 1000L * 1000L
            "TI", "TIB" -> 1000L * 1000L * 1000L * 1000L
            "GI", "GIB" -> 1000L * 1000L * 1000L
            "MI", "MIB" -> 1000L * 1000L
            "KI", "KIB" -> 1000L
            "B" -> 1L
            else -> if (unit.isEmpty()) 1L else throw IllegalArgumentException("Invalid memory unit: $unit")
        }

        log("$resourceString -> $value x $multiplier = ${value.toLong() * multiplier} bytes", DEBUG)

        return (value.toLong() * multiplier)
    }

    // check the individual resource limits supplied
    private fun checkResource(requestString: String, limitString: String): Boolean {
        val limit: Long
        val request: Long

        try {
            limit = parseResourceString(limitString)
            request = parseResourceString(requestString)
        }
        catch(e: IllegalArgumentException) {
            report.addEntry(PreInstallPlugin.ReportEntry("Parse resource strings", false, e))
            return false
        }

        return limit >= request
    }

    // use the checkResource function to check each individual resource
    private fun checkResources(resources: ResourceConfig, name: String) {
        val requests: ResourceValues = resources.requests
        val limits: ResourceValues = resources.limits

        log("${name.uppercase()}:", INFO)
        log("Requests: \n\t memory - ${requests.memory}\n\t cpu - ${requests.cpu}", INFO)
        log("Limits: \n\t memory - ${limits.memory}\n\t cpu - ${limits.cpu}", INFO)

        val check = checkResource(requests.memory, limits.memory) and checkResource(requests.cpu, limits.cpu)

        if (check) {
            report.addEntry(PreInstallPlugin.ReportEntry("$name requests do not exceed limits", true))
        }
        else {
            report.addEntry(PreInstallPlugin.ReportEntry("$name requests do not exceed limits", false))
        }
    }

    override fun call(): Int {
        register(verbose, debug)

        val yaml: Configurations
        try {
            yaml = parseYaml<Configurations>(path)
            report.addEntry(PreInstallPlugin.ReportEntry("Parse resource properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(PreInstallPlugin.ReportEntry("Parse resource properties from YAML", false, e))
            log(report.failingTests(), ERROR)
            return 1
        }

        checkResources(yaml.resources, "resources")

        yaml.bootstrap?.let { checkResources(it.resources, "bootstrap") }
        yaml.workers?.db?.let { checkResources(it.resources, "DB") }
        yaml.workers?.flow?.let { checkResources(it.resources, "flow") }
        yaml.workers?.membership?.let { checkResources(it.resources, "membership") }
        yaml.workers?.rest?.let { checkResources(it.resources, "rest") }
        yaml.workers?.p2pLinkManager?.let { checkResources(it.resources, "P2P link manager") }
        yaml.workers?.p2pGateway?.let { checkResources(it.resources, "P2P gateway") }

        if (report.testsPassed() == 0) {
            log(report.toString(), INFO)
        } else {
            log(report.failingTests(), ERROR)
        }

        return report.testsPassed()
    }
}