package net.corda.cli.plugins.common

import net.corda.sdk.profile.CliProfile
import net.corda.sdk.profile.ProfileKey
import net.corda.sdk.profile.ProfileUtils
import picocli.CommandLine.Option

abstract class RestCommand {
    @Option(
        names = ["--profile"],
        required = false,
        description = ["Profile name"]
    )
    var profileName: String? = null

    @Option(
        names = ["-t", "--target"],
        required = false,
        description = ["The target address of the REST Endpoint (e.g. `https://host:port`)"],
    )
    lateinit var targetUrl: String

    @Option(
        names = ["-u", "--user"],
        required = false,
        description = ["REST user name"],
    )
    lateinit var username: String

    @Option(
        names = ["-p", "--password"],
        required = false,
        description = ["REST password"],
    )
    lateinit var password: String

    @Option(
        names = ["-pv", "--protocol-version"],
        required = false,
        description = ["Minimum protocol version. Defaults to 1 if missing."]
    )
    var minimumServerProtocolVersion: Int = 1

    @Option(
        names = ["-y", "--yield"],
        required = false,
        description = [
            "Duration in seconds to patiently wait till REST connection will become available. " +
                "Defaults to 10 seconds if missing."
        ]
    )
    var waitDurationSeconds: Int = 10

    @Option(
        names = ["-k", "--insecure"],
        required = false,
        description = ["Allow insecure server connections with SSL. Defaults to 'false' if missing."]
    )
    var insecure: Boolean = false

    open fun run() {
        checkAndSetProfileParams()
    }

    open fun call(): Int {
        checkAndSetProfileParams()
        return 0
    }

    @Suppress("ThrowsCount")
    private fun checkAndSetProfileParams() {
        val profile = if (profileName != null) ProfileUtils.getProfile(profileName!!) else CliProfile(emptyMap())

        username = if (::username.isInitialized) {
            username
        } else {
            profile.properties[ProfileKey.REST_USERNAME.name.lowercase()]
                ?: throw IllegalArgumentException("username must be provided either directly or through a profile.")
        }

        password = if (::password.isInitialized) {
            password
        } else {
            if (!profile.properties.containsKey(ProfileKey.REST_PASSWORD.name.lowercase())) {
                throw IllegalArgumentException("password must be provided either directly or through a profile.")
            } else {
                ProfileUtils.getPasswordProperty(profile, ProfileKey.REST_PASSWORD.name.lowercase())
            }
        }

        targetUrl = if (::targetUrl.isInitialized) {
            targetUrl
        } else {
            profile.properties[ProfileKey.REST_ENDPOINT.name.lowercase()]
                ?: throw IllegalArgumentException("targetUrl must be provided either directly or through a profile.")
        }
    }
}
