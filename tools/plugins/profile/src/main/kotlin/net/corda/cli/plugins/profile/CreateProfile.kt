package net.corda.cli.plugins.profile

import picocli.CommandLine

@CommandLine.Command(name = "create-profile", description = ["create profile."], mixinStandardHelpOptions = true)
class CreateProfile : Runnable {
    override fun run() {
        println("Hello from the example plugin!")
    }
}