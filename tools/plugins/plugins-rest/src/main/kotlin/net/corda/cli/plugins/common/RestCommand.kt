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
        names = ["-y", "--yield"],
        required = false,
        description = [
            "Duration in seconds to patiently wait till REST connection will become available. " +
                "Defaults to 10 seconds if missing."
        ]
    )
    open var waitDurationSeconds: Int = 10

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

    private fun checkAndSetProfileParams() {
        val profile = if (profileName != null) ProfileUtils.getProfile(profileName!!) else CliProfile(emptyMap())

        username = if (::username.isInitialized) {
            username
        } else {
            requireNotNull(profile.properties[ProfileKey.REST_USERNAME.name.lowercase()]) {
                "username must be provided either directly or through a profile."
            }
        }

        password = if (::password.isInitialized) {
            password
        } else {
            requireNotNull(profile.properties[ProfileKey.REST_PASSWORD.name.lowercase()]) {
                "password must be provided either directly or through a profile."
            }
            ProfileUtils.getPasswordProperty(profile, ProfileKey.REST_PASSWORD.name.lowercase())
        }

        targetUrl = if (::targetUrl.isInitialized) {
            targetUrl
        } else {
            requireNotNull(profile.properties[ProfileKey.REST_ENDPOINT.name.lowercase()]) {
                "targetUrl must be provided either directly or through a profile."
            }
        }
    }
}
