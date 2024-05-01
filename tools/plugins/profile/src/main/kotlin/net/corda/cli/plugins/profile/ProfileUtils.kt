package net.corda.cli.plugins.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

object ProfileUtils {
    val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val profileFile = File(System.getProperty("user.home"), ".corda/cli/profile.yaml")

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
