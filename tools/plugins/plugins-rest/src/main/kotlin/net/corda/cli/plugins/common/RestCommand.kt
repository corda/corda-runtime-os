package net.corda.cli.plugins.common

import net.corda.sdk.profile.ProfileParameterConsumer
import picocli.CommandLine.Option

abstract class RestCommand {
    @Option(
        names = ["--profile"],
        required = false,
        description = ["Profile name"]
    )
    lateinit var profileName: String

    @Option(
        names = ["-t", "--target"],
        required = false,
        description = ["The target address of the REST Endpoint (e.g. `https://host:port`)"],
        parameterConsumer = ProfileParameterConsumer::class
    )
    lateinit var targetUrl: String

    @Option(
        names = ["-u", "--user"],
        required = false,
        description = ["REST user name"],
        parameterConsumer = ProfileParameterConsumer::class
    )
    lateinit var username: String

    @Option(
        names = ["-p", "--password"],
        required = false,
        description = ["REST password"],
        parameterConsumer = ProfileParameterConsumer::class
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

    init {
        if (::profileName.isInitialized) {
            println("profileName: $profileName")
        } else {
            println("profileName has not been initialized")
        }

        if (::username.isInitialized) {
            println(", username: $username")
        } else {
            println("username has not been initialized")
        }

        if (::password.isInitialized) {
            println("password: $password, ")
        } else {
            println("password has not been initialized")
        }

        if (::targetUrl.isInitialized) {
            println("targetUrl: $targetUrl")
        } else {
            println("targetUrl has not been initialized")
        }
    }
}
