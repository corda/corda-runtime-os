package net.corda.introspiciere.cli

import picocli.CommandLine

abstract class BaseCommand : Runnable {
    @CommandLine.Option(names = ["--endpoint"], defaultValue = "http://localhost:7070")
    protected lateinit var endpoint: String

    @CommandLine.Spec
    protected lateinit var spec: CommandLine.Model.CommandSpec
}