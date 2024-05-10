package net.corda.sdk.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

enum class ProfileKey {
    REST_USERNAME,
    REST_PASSWORD,
    ENDPOINT,
    JDBC_USERNAME,
    JDBC_PASSWORD,
    DATABASE_URL
}

object ProfileUtils {
    val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    var profileFile = File(System.getProperty("user.home"), ".corda/cli/profile.yaml")
    val VALID_KEYS = ProfileKey.values().map { it.name.lowercase() }

    fun isValidKey(key: String): Boolean {
        return VALID_KEYS.contains(key)
    }

    fun loadProfiles(): Map<String, Any> {
        return if (profileFile.exists()) {
            objectMapper.readValue(profileFile, jacksonTypeRef<Map<String, Any>>())
        } else {
            emptyMap()
        }
    }

    fun saveProfiles(profiles: Map<String, Any>) {
        profileFile.parentFile.mkdirs()
        objectMapper.writeValue(profileFile, profiles)
    }
}