package net.corda.cli.application.services

import java.io.File
import java.nio.file.Paths

class Files {
    companion object {
        val cliHomeDir: File by lazy { Paths.get(System.getProperty("user.home"), "/.corda/cli/").toFile() }
        val profile: File by lazy { Paths.get(System.getProperty("user.home"), "/.corda/cli/profile.yaml").toFile() }
    }
}