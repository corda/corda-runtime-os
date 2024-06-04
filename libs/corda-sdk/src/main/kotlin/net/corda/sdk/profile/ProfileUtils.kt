package net.corda.sdk.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import java.io.File
import java.io.IOException

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
            values().joinToString(System.lineSeparator()) { key ->
                "${key.name.lowercase()}: ${key.description},"
            }
        }

        fun isValidKey(key: String): Boolean {
            return validKeys.contains(key.lowercase())
        }

        fun getKeysWithDescriptions(): String {
            return cachedDescriptions
        }

        // Repeating enum names/descriptions since it has to be compile-time const to be usable in PicoCLI annotations.
        const val CONST_KEYS_WITH_DESCRIPTIONS: String = """
            rest_username: Username for REST API,
            rest_password: Password for REST API,
            rest_endpoint: Endpoint for the REST API,
            jdbc_username: Username for JDBC connection,
            jdbc_password: Password for JDBC connection,
            database_url: URL for the database,
        """
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

    fun getProfile(profileName: String): CliProfile {
        val profiles = loadProfiles()
        val profile = profiles[profileName]
        return requireNotNull(profile) { "Profile with name $profileName does not exist."}
    }

    fun getPasswordProperty(profile: CliProfile, propertyName: String): String {
        val encryptedPassword = requireNotNull(profile.properties[propertyName]) {
            "Property with name $propertyName does not exist."
        }
        val salt = profile.properties["${propertyName}_salt"]
        if (salt == null) {
            throw IllegalArgumentException("Salt for property $propertyName does not exist.")
        }
        val secretEncryptionUtil = SecretEncryptionUtil()
        val decryptedPassword = secretEncryptionUtil.decrypt(encryptedPassword, salt, salt)
        return decryptedPassword
    }

    fun getDbConnectionDetails(profile: CliProfile): Triple<String?, String?, String?> {
        val jdbcUrl = profile.properties[ProfileKey.DATABASE_URL.name.lowercase()]
        val user = profile.properties[ProfileKey.JDBC_USERNAME.name.lowercase()]
        val password = profile.properties[ProfileKey.JDBC_PASSWORD.name.lowercase()]?.let {
            getPasswordProperty(profile, ProfileKey.JDBC_PASSWORD.name.lowercase())
        }
        return Triple(jdbcUrl, user, password)
    }
}
