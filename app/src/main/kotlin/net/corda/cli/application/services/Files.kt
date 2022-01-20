package net.corda.cli.application.services

import java.io.File
import java.nio.file.Paths

class Files {
    companion object {

        fun cliHomeDir() : File {
            return if(System.getenv("CORDA_CLI_HOME_DIR").isNullOrEmpty()){
                Paths.get(System.getProperty("user.home"), "/.corda/cli/").toFile()
            } else {
                Paths.get(System.getenv("CORDA_CLI_HOME_DIR")).toFile()
            }
        }

        val profile: File by lazy { Paths.get(cliHomeDir().path, "/.corda/cli/profile.yaml").toFile() }
    }
}