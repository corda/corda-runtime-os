package net.corda.cli.plugins.common

import org.pf4j.ExtensionPoint
import picocli.CommandLine.Option

abstract class RestCommand : ExtensionPoint {

    @Option(
        names = ["-t", "--target"],
        required = true,
        description = ["The target address of the REST Endpoint (e.g. `https://host:port`)"]
    )
    lateinit var targetUrl: String

    @Option(
        names = ["-u", "--user"],
        description = ["REST user name"],
        required = true
    )
    lateinit var username: String

    @Option(
        names = ["-p", "--password"],
        description = ["REST password"],
        required = true
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
        description = ["Duration in seconds to patiently wait till REST connection will become available. " +
                "Defaults to 10 seconds if missing."]
    )
    var waitDurationSeconds: Int = 10
}