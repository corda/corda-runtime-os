package net.corda.cli.plugins.packaging

import picocli.CommandLine

@CommandLine.Command(
    name = "verify",
    description = ["Verifies a CPI signature."]
)
class VerifyCpi : Runnable {
    override fun run() {
        TODO("Implement verify method")
    }
}