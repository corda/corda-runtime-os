package net.corda.introspiciere.cli

import net.corda.introspiciere.http.IntrospiciereHttpClient
import picocli.CommandLine

abstract class BaseCommand : Runnable {
    @CommandLine.Option(names = ["--endpoint"], defaultValue = "http://localhost:7070")
    protected lateinit var endpoint: String

    protected val httpClient by lazy { IntrospiciereHttpClient(endpoint) }
}