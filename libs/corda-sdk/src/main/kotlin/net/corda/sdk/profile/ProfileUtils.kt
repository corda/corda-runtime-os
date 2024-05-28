package net.corda.sdk.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

enum class ProfileKey(val description: String) {
    REST_USERNAME("Username for REST API"),
    REST_PASSWORD("Password for REST API"),
    REST_ENDPOINT("Endpoint for the REST API"),
    JDBC_USERNAME("Username for JDBC connection"),
    JDBC_PASSWORD("Password for JDBC connection"),
    DATABASE_URL("URL for the database")
}

object ProfileUtils {
    private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private var profileFile: File
    private val VALID_KEYS = ProfileKey.values().map { it.name.lowercase() }

    init {
        profileFile = File(System.getProperty("user.home"), ".corda/cli/profile.yaml")
    }

    fun initialize(file: File) {
        profileFile = file
    }

    fun isValidKey(key: String): Boolean {
        return VALID_KEYS.contains(key)
    }

    fun loadProfiles(): Map<String, Map<String, String>> {
        return try {
            if (profileFile.exists()) {
                objectMapper.readValue(profileFile, jacksonTypeRef<Map<String, Map<String, String>>>())
            } else {
                emptyMap()
            }
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException("Invalid profile.yaml file format", e)
        }
    }

    fun saveProfiles(profiles: Map<String, Any>) {
        profileFile.parentFile.mkdirs()
        objectMapper.writeValue(profileFile, profiles)
    }

    fun getProfileKeysWithDescriptions(): String {
        return ProfileKey.values().joinToString("\n") { key ->
            "${key.name.lowercase()}: ${key.description},"
        }
    }
}
