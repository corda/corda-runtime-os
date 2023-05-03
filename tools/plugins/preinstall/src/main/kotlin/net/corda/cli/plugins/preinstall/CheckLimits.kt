package net.corda.cli.plugins.preinstall

import net.corda.cli.plugins.preinstall.PreInstallPlugin.ResourceConfig
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ResourceValues
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Configurations
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
import picocli.CommandLine
import picocli.CommandLine.Parameters
import java.util.concurrent.Callable

@CommandLine.Command(name = "check-limits", description = ["Check the resource limits have been assigned correctly."])
class CheckLimits : Callable<Int>, PluginContext() {

    @Parameters(index = "0", description = ["YAML file containing resource limit overrides for the corda install"])
    lateinit var path: String

    class ResourceLimitsExceededException(message: String) : Exception(message)

    private var hasCheckedLimits = false

    private val logger = getLogger()

    // split resource into a digit portion and a unit portion
    private fun parseResourceString(resourceString: String): Long {
        val regex = Regex("(\\d+)([EPTGMK]?i?[Bb]?)?")

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

        logger.debug("$resourceString -> $value x $multiplier = ${value.toLong() * multiplier} bytes")

        return (value.toLong() * multiplier)
    }

    // check the individual resource limits supplied
    private fun checkResource(requestString: String, limitString: String) {
        val limit: Long = parseResourceString(limitString)
        val request: Long = parseResourceString(requestString)

        if (limit < request) {
            throw ResourceLimitsExceededException("Request ($requestString) is greater than it's limit ($limitString)")
        }
    }

    // use the checkResource function to check each individual resource
    private fun checkResources(resources: ResourceConfig, name: String) {
        hasCheckedLimits = true
        val requests: ResourceValues = resources.requests
        val limits: ResourceValues = resources.limits

        logger.info("${name.uppercase()}:")

        try {
            if (requests.memory != null || limits.memory != null) {
                if (requests.memory == null || limits.memory == null) {
                    report.addEntry(ReportEntry("${name.uppercase()} memory resources contains both a request and a limit", false))
                    return
                }
                report.addEntry(ReportEntry("${name.uppercase()} memory resources contains both a request and a limit", true))
                logger.info("Memory: \n\t request - ${requests.memory}\n\t limit - ${limits.memory}")
                checkResource(requests.memory, limits.memory)
            }
            if (requests.cpu != null || limits.cpu != null) {
                if (requests.cpu == null || limits.cpu == null) {
                    report.addEntry(ReportEntry("${name.uppercase()} cpu resources contains both a request and a limit", false))
                    return
                }
                report.addEntry(ReportEntry("${name.uppercase()} cpu resources contains both a request and a limit", true))
                logger.info("CPU: \n\t request - ${requests.cpu}\n\t limit - ${limits.cpu}")
                checkResource(requests.cpu, limits.cpu)
            }

            report.addEntry(ReportEntry("Parse resource strings", true))
            report.addEntry(ReportEntry("$name requests do not exceed limits", true))
        } catch(e: IllegalArgumentException) {
            report.addEntry(ReportEntry("Parse resource strings", false, e))
            return
        } catch (e: ResourceLimitsExceededException) {
            report.addEntry(ReportEntry("$name requests do not exceed limits", false, e))
            return
        }
    }

    override fun call(): Int {
        val yaml: Configurations
        try {
            yaml = parseYaml<Configurations>(path)
            report.addEntry(ReportEntry("Parse resource properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse resource properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        yaml.resources?.let { checkResources(it, "resources") }
        yaml.bootstrap?.let { checkResources(it.resources, "bootstrap") }
        yaml.workers?.db?.let { checkResources(it.resources, "DB") }
        yaml.workers?.flow?.let { checkResources(it.resources, "flow") }
        yaml.workers?.membership?.let { checkResources(it.resources, "membership") }
        yaml.workers?.rest?.let { checkResources(it.resources, "rest") }
        yaml.workers?.p2pLinkManager?.let { checkResources(it.resources, "P2P link manager") }
        yaml.workers?.p2pGateway?.let { checkResources(it.resources, "P2P gateway") }

        if (report.testsPassed() == 0) {
            logger.info(report.toString())
        } else {
            logger.error(report.failingTests())
        }

        if (!hasCheckedLimits) {
            logger.info("No resource requests or limits were found.")
        }

        return report.testsPassed()
    }
}