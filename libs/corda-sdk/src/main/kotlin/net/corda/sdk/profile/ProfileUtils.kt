package net.corda.sdk.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import picocli.CommandLine
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.util.*

data class CliProfile(val properties: Map<String, String>)

enum class ProfileKey(val description: String) {
    REST_USERNAME("Username for REST API"),
    REST_PASSWORD("Password for REST API"),
    REST_ENDPOINT("Endpoint for the REST API"),
    JDBC_USERNAME("Username for JDBC connection"),
    JDBC_PASSWORD("Password for JDBC connection"),
    DATABASE_URL("URL for the database");

    companion object {
        private val validKeys: List<String> by lazy { values().map { it.name.lowercase() } }
        private val cachedDescriptions: String by lazy {
            values().joinToString("\n") { key ->
                "${key.name.lowercase()}: ${key.description},"
            }
        }

        fun isValidKey(key: String): Boolean {
            return validKeys.contains(key.lowercase())
        }

        fun getKeysWithDescriptions(): String {
            return cachedDescriptions
        }
    }
}

class ProfileParameterConsumer : CommandLine.IParameterConsumer {
    override fun consumeParameters(args: Stack<String>?, argSpec: CommandLine.Model.ArgSpec?, commandSpec: CommandLine.Model.CommandSpec?) {
        println("Consuming parameters")
        val value = args?.pop()
        println("value: $value, argSpec: ${argSpec?.paramLabel().strip()}")

        if (value == null) {
            val profileOption = commandSpec?.findOption("profile")
            val profileName = profileOption?.getValue() as String?
            println("profileName: $profileName, argSpec: ${argSpec?.paramLabel()}")
            if (profileName == null) {
                throw IllegalArgumentException("${argSpec?.paramLabel()} must be provided either directly or through a profile.")
            }
            val profile = ProfileUtils.getProfile(profileName)
            val profileValueForOption = profile[argSpec?.paramLabel()]
            argSpec?.setValue(profileValueForOption)
        } else {
            argSpec?.setValue(value)
        }
    }
}

object ProfileUtils {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private var profileFile: File

    init {
        profileFile = File(System.getProperty("user.home"), ".corda/cli/profile.yaml")
    }

    fun initialize(file: File) {
        profileFile = file
    }

    fun loadProfiles(): Map<String, CliProfile> {
        return try {
            if (profileFile.exists()) {
                val profilesMap = objectMapper.readValue(profileFile, jacksonTypeRef<Map<String, Map<String, String>>>())
                profilesMap.mapValues { (_, properties) ->
                    CliProfile(properties)
                }
            } else {
                emptyMap()
            }
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException("Invalid profile.yaml file format", e)
        }
    }

    fun saveProfiles(profiles: Map<String, CliProfile>) {
        try {
            val profilesMap = profiles.mapValues { (_, profile) ->
                profile.properties
            }
            objectMapper.writeValue(profileFile, profilesMap)
        } catch (e: IOException) {
            throw IOException("Failed to save profiles to file", e)
        }
    }

    fun getProfile(profileName: String): Map<String, String> {
        val profiles = loadProfiles()
        @Suppress("UNCHECKED_CAST")
        return (profiles[profileName] as? Map<String, String>) ?: emptyMap()
    }
}
