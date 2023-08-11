package net.corda.cli.plugins.network

import picocli.CommandLine.Command

@Command(
    name = "operate",
    description = [
        "Operate on the network",
        "This sub command should only be used for internal development"
    ],
    subcommands = [
        AllowClientCertificate::class,
        ExportGroupPolicy::class,
        GetPreAuthRules::class,
        AddPreAuthRule::class,
        DeletePreAuthRule::class
    ]
)
class Operate : Runnable {
    override fun run() {
        // Implementation for the operate command
    }
}