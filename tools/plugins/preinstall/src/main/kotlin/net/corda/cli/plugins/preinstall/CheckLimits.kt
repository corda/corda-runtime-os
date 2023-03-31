package net.corda.cli.plugins.preinstall

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.ValueInstantiationException
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import picocli.CommandLine
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option
import java.io.File

data class ResourceValues(
    @JsonProperty("memory")
    val memory: String,
    @JsonProperty("cpu")
    val cpu: String
)

data class ResourceConfig(
    @JsonProperty("requests")
    val requests: ResourceValues,
    @JsonProperty("limits")
    val limits: ResourceValues
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Resources(
    @JsonProperty("resources")
    val resources: ResourceConfig
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Workers(
    @JsonProperty("db")
    val db: Resources?,
    @JsonProperty("flow")
    val flow: Resources?,
    @JsonProperty("membership")
    val membership: Resources?,
    @JsonProperty("rest")
    val rest: Resources?,
    @JsonProperty("p2pLinkManager")
    val p2pLinkManager: Resources?,
    @JsonProperty("p2pGateway")
    val p2pGateway: Resources?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Configurations(
    @JsonProperty("bootstrap")
    val bootstrap: Resources?,
    @JsonProperty("workers")
    val workers: Workers?,
    @JsonProperty("resources")
    val resources: ResourceConfig
)

@CommandLine.Command(name = "check-limits", description = ["Check the resource limits have been assigned."])
class CheckLimits : Runnable {

    @Parameters(index = "0", description = ["The yaml file containing resource limit overrides for the corda install."])
    lateinit var path: String

    @Option(names = ["-v", "--verbose"], description = ["Display additional information when checking resources"])
    var verbose: Boolean = false

    @Option(names = ["-d", "--debug"], description = ["Show information about limit calculation for debugging purposes"])
    var debug: Boolean = false

    companion object LogLevel {
        const val ERROR: Int = 0
        const val INFO: Int = 1
        const val DEBUG: Int = 2
    }

    // used for logging
    private fun log(s: String, level: Int) {
        when (level) {
            ERROR -> println("[ERROR] $s")
            INFO -> if (verbose) println("[INFO] $s")
            DEBUG -> if (debug) println("[DEBUG] $s")
        }
    }

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
            log(e.message!!, ERROR)
            return false
        }

        return limit >= request
    }

    // use the checkResource function to check each individual resource
    private fun checkResources(resources: ResourceConfig, name: String): Boolean {
        val requests: ResourceValues = resources.requests
        val limits: ResourceValues = resources.limits

        log("${name.uppercase()}:", INFO)
        log("Requests: \n\t memory - ${requests.memory}\n\t cpu - ${requests.cpu}", INFO)
        log("Limits: \n\t memory - ${limits.memory}\n\t cpu - ${limits.cpu}", INFO)

        val check = checkResource(requests.memory, limits.memory) and checkResource(requests.cpu, limits.cpu)

        if (check) {
            log("Resource requests for $name are appropriate and are under the set limits\n", INFO)
        }
        else {
            log("Resource requests for $name have been exceeded!\n", ERROR)
        }

        return check
    }

    override fun run() {
        log("Working Directory = ${System.getProperty("user.dir")}\n", INFO)
        val file = File(path)

        if(!file.isFile) {
            log("File does not exist", ERROR)
            return
        }

        lateinit var yaml: Configurations
        try {
            val mapper: ObjectMapper = YAMLMapper()
            yaml = mapper.readValue(file, Configurations::class.java)
        }
        catch ( e: ValueInstantiationException ) {
            log("Could not parse the YAML file at $path: ${e.message}", ERROR)
            return
        }

        var check: Boolean = checkResources(yaml.resources, "resources")

        yaml.bootstrap?.let { check = check && checkResources(it.resources, "bootstrap") }
        yaml.workers?.db?.let { check = check && checkResources(it.resources, "DB") }
        yaml.workers?.flow?.let { check = check && checkResources(it.resources, "flow") }
        yaml.workers?.membership?.let { check = check && checkResources(it.resources, "membership") }
        yaml.workers?.rest?.let { check = check && checkResources(it.resources, "rest") }
        yaml.workers?.p2pLinkManager?.let { check = check && checkResources(it.resources, "P2P link manager") }
        yaml.workers?.p2pGateway?.let { check = check && checkResources(it.resources, "P2P gateway") }

        if (check) {println("[INFO] All resource requests are appropriate and are under the set limits.")}
    }
}