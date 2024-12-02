package net.corda.sdk.profile

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import net.corda.libs.configuration.secret.SecretEncryptionUtil
import java.io.File
import java.io.IOException
import java.util.UUID

data class CliProfile(val properties: Map<String, String>)

enum class ProfileKey {
    REST_USERNAME,
    REST_PASSWORD,
    REST_ENDPOINT,
    JDBC_USERNAME,
    JDBC_PASSWORD,
    DATABASE_URL;

    companion object {
        // names/descriptions must be compile-time const to be usable in PicoCLI annotations.
        const val CONST_KEYS_WITH_DESCRIPTIONS: String = """
            rest_username: Username for REST API,
            rest_password: Password for REST API,
            rest_endpoint: Endpoint for the REST API,
            jdbc_username: Username for JDBC connection,
            jdbc_password: Password for JDBC connection,
            database_url: URL for the database,
        """

        private val validKeys: Set<String> by lazy { values().map { it.name.lowercase() }.toSet() }

        fun isValidKey(key: String): Boolean {
            return validKeys.contains(key.lowercase())
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

    fun getProfile(profileName: String): CliProfile {
        val profiles = loadProfiles()
        val profile = profiles[profileName]
        return requireNotNull(profile) { "Profile with name $profileName does not exist." }
    }

    fun createPropertiesMapFromCliArguments(properties: Set<String>): Map<String, String> {
        val secretEncryptionUtil = SecretEncryptionUtil()
        val salt = UUID.randomUUID().toString()
        val profileProperties = mutableMapOf<String, String>()

        properties.forEach { property ->
            val (key, value) = property.split("=")
            if (!ProfileKey.isValidKey(key)) {
                throw IllegalArgumentException("Invalid key '$key'. Allowed keys are:\n ${ProfileKey.CONST_KEYS_WITH_DESCRIPTIONS}")
            }
            if (key.lowercase().contains("password")) {
                val encryptedPassword = secretEncryptionUtil.encrypt(value, salt, salt)
                profileProperties[key] = encryptedPassword
                profileProperties["${key}_salt"] = salt
            } else {
                profileProperties[key] = value
            }
        }

        return profileProperties
    }

    fun getPasswordProperty(profile: CliProfile, propertyName: String): String {
        val encryptedPassword = requireNotNull(profile.properties[propertyName]) {
            "Property with name $propertyName does not exist."
        }
        val salt = requireNotNull(profile.properties["${propertyName}_salt"]) {
            "Salt for property $propertyName does not exist."
        }

        val secretEncryptionUtil = SecretEncryptionUtil()
        return secretEncryptionUtil.decrypt(encryptedPassword, salt, salt)
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
