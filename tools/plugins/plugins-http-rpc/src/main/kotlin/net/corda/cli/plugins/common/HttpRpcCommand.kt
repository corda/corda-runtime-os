package net.corda.cli.plugins.common

import org.pf4j.ExtensionPoint
import picocli.CommandLine.Option

abstract class HttpRpcCommand : ExtensionPoint {

    @Option(
        names = ["-t", "--target"],
        required = true,
        description = ["The target address of the HTTP RPC Endpoint (e.g. `https://host:port`)"]
    )
    lateinit var targetUrl: String

    @Option(
        names = ["-u", "--user"],
        description = ["User name"],
        required = true
    )
    lateinit var username: String

    @Option(
        names = ["-p", "--password"],
        description = ["Password"],
        required = true
    )
    lateinit var password: String

    @Option(
        names = ["-pv", "--protocol-version"],
        required = false,
        description = ["Minimum protocol version."]
    )
    var minimumServerProtocolVersion: Int = 1
}