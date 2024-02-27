package net.corda.cli.plugins.preinstall

import java.util.concurrent.Callable
import net.corda.cli.plugins.preinstall.PreInstallPlugin.CordaValues
import net.corda.cli.plugins.preinstall.PreInstallPlugin.PluginContext
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ReportEntry
import net.corda.cli.plugins.preinstall.PreInstallPlugin.ResourceValues
import net.corda.cli.plugins.preinstall.PreInstallPlugin.Resources
import picocli.CommandLine
import picocli.CommandLine.Parameters

@CommandLine.Command(
    name = "check-limits",
    description = ["Check the resource limits have been assigned correctly."],
    mixinStandardHelpOptions = true
)
class CheckLimits : Callable<Int>, PluginContext() {

    @Parameters(index = "0", description = ["YAML file containing resource limit overrides for the Corda install"])
    lateinit var path: String

    class ResourceLimitsExceededException(message: String) : Exception(message)

    private var defaultRequests: ResourceValues? = null
    private var defaultLimits: ResourceValues? = null

    private fun parseMemoryString(memoryString: String): Double {
        val regex = Regex("(\\d+)([EPTGMKk]?i?[Bb]?)?")

        val (value, unit) = regex.matchEntire(memoryString)?.destructured
            ?: throw IllegalArgumentException("Invalid memory string format: $memoryString")

        // https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#setting-requests-and-limits-for-local-ephemeral-storage
        val multiplier: Double =
            when (unit.uppercase()) {
                "E", "EB" -> 1000.0 * 1000 * 1000 * 1000 * 1000 * 1000
                "P", "PB" -> 1000.0 * 1000 * 1000 * 1000 * 1000
                "T", "TB" -> 1000.0 * 1000 * 1000 * 1000
                "G", "GB" -> 1000.0 * 1000 * 1000
                "M", "MB" -> 1000.0 * 1000
                "K", "KB" -> 1000.0
                "EI", "EIB" -> 1024.0 * 1024 * 1024 * 1024 * 1024 * 1024
                "PI", "PIB" -> 1024.0 * 1024 * 1024 * 1024 * 1024
                "TI", "TIB" -> 1024.0 * 1024 * 1024 * 1024
                "GI", "GIB" -> 1024.0 * 1024 * 1024
                "MI", "MIB" -> 1024.0 * 1024
                "KI", "KIB" -> 1024.0
                "", "B" -> 1.0
                else -> throw IllegalArgumentException("Invalid memory unit: $unit")
            }

        val result: Double = value.toDouble() * multiplier

        logger.debug("{} -> {} x {} = {} bytes", memoryString, value, multiplier, result)
        return result
    }

    private fun parseCpuString(cpuString: String): Double {
        val regex = Regex("(\\d*\\.?\\d+)([EPTGMkm])?")

        val (value, unit) = regex.matchEntire(cpuString)?.destructured
            ?: throw IllegalArgumentException("Invalid CPU string format: $cpuString")

        // https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#setting-requests-and-limits-for-local-ephemeral-storage
        val multiplier: Double =
            when (unit) {
                "E" -> 1000.0 * 1000 * 1000 * 1000 * 1000 * 1000
                "P" -> 1000.0 * 1000 * 1000 * 1000 * 1000
                "T" -> 1000.0 * 1000 * 1000 * 1000
                "G" -> 1000.0 * 1000 * 1000
                "M" -> 1000.0 * 1000
                "k" -> 1000.0
                "" -> 1.0
                "m" -> 0.001
                else -> throw IllegalArgumentException("Invalid CPU unit: $unit")
            }

        val result: Double = value.toDouble() * multiplier

        logger.debug("{} -> {} x {} = {}", cpuString, value, multiplier, result)
        return result
    }

    // use the checkResource function to check each individual resource
    private fun checkResources(resources: Resources?, name: String) {
        val requests: ResourceValues? = resources?.requests ?: defaultRequests
        val limits: ResourceValues? = resources?.limits ?: defaultLimits

        checkCpu(requests, limits, name)
        checkMemory(requests, limits, name)
    }

    private fun checkCpu(requests: ResourceValues?, limits: ResourceValues?, name: String) {
        try {
            if (requests?.cpu == null) {
                requests?.cpu = defaultRequests?.cpu
            }
            if (limits?.cpu == null) {
                limits?.cpu = defaultLimits?.cpu
            }

            if (requests?.cpu != null || limits?.cpu != null) {
                if (requests?.cpu == null || limits?.cpu == null) {
                    report.addEntry(ReportEntry("${name.uppercase()} cpu resources contains both a request and a limit", false))
                    return
                }
                report.addEntry(ReportEntry("${name.uppercase()} cpu resources contains both a request and a limit", true))
                logger.info("${name.uppercase()} CPU: \n\t request - ${requests.cpu}\n\t limit - ${limits.cpu}")
                val limit: Double = parseCpuString(limits.cpu!!)
                val request: Double = parseCpuString(requests.cpu!!)
                report.addEntry(ReportEntry("Parse \"$name\" cpu resource strings", true))

                if (limit >= request) {
                    report.addEntry(ReportEntry("$name cpu requests do not exceed limits", true))
                } else {
                    report.addEntry(ReportEntry("$name cpu requests do not exceed limits", false,
                        ResourceLimitsExceededException("Request ($requests.cpu!!) is greater than it's limit ($limits.cpu!!)")))
                }
            }

        } catch(e: IllegalArgumentException) {
            report.addEntry(ReportEntry("Parse \"$name\" cpu resource strings", false, e))
        }
    }

    // use the checkResource function to check each individual resource
    private fun checkMemory(requests: ResourceValues?, limits: ResourceValues?, name: String) {
        try {
            if (requests?.memory == null) {
                requests?.memory = defaultRequests?.memory
            }
            if (limits?.memory == null) {
                limits?.memory = defaultLimits?.memory
            }

            if (requests?.memory != null || limits?.memory != null) {
                if (requests?.memory == null || limits?.memory == null) {
                    report.addEntry(ReportEntry("${name.uppercase()} memory resources contains both a request and a limit", false))
                    return
                }
                report.addEntry(ReportEntry("${name.uppercase()} memory resources contains both a request and a limit", true))
                logger.info("${name.uppercase()} Memory: \n\t request - ${requests.memory}\n\t limit - ${limits.memory}")
                val limit = parseMemoryString(limits.memory!!)
                val request = parseMemoryString(requests.memory!!)
                report.addEntry(ReportEntry("Parse \"$name\" memory resource strings", true))

                if (limit >= request) {
                    report.addEntry(ReportEntry("$name memory requests do not exceed limits", true))
                } else {
                    report.addEntry(ReportEntry("$name memory requests do not exceed limits", false,
                        ResourceLimitsExceededException("Request ($requests.memory!!) is greater than it's limit ($limits.memory!!)")))
                }
            }

        } catch(e: IllegalArgumentException) {
            report.addEntry(ReportEntry("Parse \"$name\" memory resource strings", false, e))
        }
    }

    override fun call(): Int {
        val yaml: CordaValues
        try {
            yaml = parseYaml<CordaValues>(path)
            report.addEntry(ReportEntry("Parse resource properties from YAML", true))
        } catch (e: Exception) {
            report.addEntry(ReportEntry("Parse resource properties from YAML", false, e))
            logger.error(report.failingTests())
            return 1
        }

        yaml.resources?.let {
            defaultLimits = it.limits
            defaultRequests = it.requests
        }

        checkResources(yaml.bootstrap.resources, "bootstrap")

        yaml.workers.forEach {
            checkResources(it.value.resources, it.key)
        }

        return if (report.testsPassed()) {
            logger.info(report.toString())
            0
        } else {
            logger.error(report.failingTests())
            1
        }
    }
}