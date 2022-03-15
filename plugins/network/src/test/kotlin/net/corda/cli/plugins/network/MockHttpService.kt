package net.corda.cli.plugins.network

import net.corda.cli.api.services.HttpService
import picocli.CommandLine

class MockHttpService : HttpService {

    @CommandLine.Option(names = ["-u", "--user"], description = ["User name"], required = true)
    override var username: String? = null

    @CommandLine.Option(names = ["-p", "--password"], description = ["Password"], required = true)
    override var password: String? = null

    @CommandLine.Option(names = ["-t", "--target-url"], description = ["Url of the target."])
    override var url: String? = null

    override fun get(endpoint: String) { println("$url$endpoint") }

    override fun put(endpoint: String, jsonBody: String) {}

    override fun patch(endpoint: String, jsonBody: String) {}

    override fun post(endpoint: String, jsonBody: String) {}

    override fun delete(endpoint: String) {}
}