package net.corda.cli.plugins.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import picocli.CommandLine
import picocli.CommandLine.Option
import java.io.File

@CommandLine.Command(
    name = "create",
    description = ["Create a new profile."],
    mixinStandardHelpOptions = true
)
class CreateProfile : Runnable {

    companion object {
        private val objectMapper: ObjectMapper = ObjectMapper(YAMLFactory()).registerModule(
            KotlinModule.Builder()
                .withReflectionCacheSize(512)
                .configure(KotlinFeature.NullToEmptyCollection, true)
                .configure(KotlinFeature.NullToEmptyMap, true)
                .configure(KotlinFeature.NullIsSameAsDefault, false)
                .configure(KotlinFeature.SingletonSupport, false)
                .configure(KotlinFeature.StrictNullChecks, false)
                .build()
        )
    }

    @Option(names = ["-n", "--name"], description = ["Profile name"], required = true)
    lateinit var profileName: String

    @Option(names = ["-u", "--username"], description = ["Username"], required = true)
    lateinit var username: String

    @Option(names = ["-p", "--password"], description = ["Password"], required = true)
    lateinit var password: String

    @Option(names = ["-e", "--endpoint"], description = ["Endpoint URL"])
    var endpoint: String? = null

    private val profileFile = File(System.getProperty("user.home"), ".corda/cli/profile.yaml")

    override fun run() {
        val profiles = loadProfiles()

        if (profiles.containsKey(profileName)) {
            println("Profile '$profileName' already exists. Overwrite? (y/n)")
            val confirmation = readLine()
            if (confirmation?.lowercase() != "y") {
                println("Profile creation aborted.")
                return
            }
        }

        val profile = mapOf(
            "username" to username,
            "password" to password,
            "endpoint" to endpoint
        )

        profiles[profileName] = profile

        saveProfiles(profiles)
        println("Profile '$profileName' created successfully.")
    }

    private fun loadProfiles(): MutableMap<String, Any> {
        return if (profileFile.exists()) {
            objectMapper.readValue(profileFile, jacksonTypeRef<MutableMap<String, Any>>())
        } else {
            mutableMapOf()
        }
    }

    private fun saveProfiles(profiles: MutableMap<String, Any>) {
        profileFile.parentFile.mkdirs()
        objectMapper.writeValue(profileFile, profiles)
    }
}
