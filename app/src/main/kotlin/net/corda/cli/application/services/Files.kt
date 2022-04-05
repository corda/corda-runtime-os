package net.corda.cli.application.services

import java.io.File
import java.io.FileInputStream
import java.nio.file.Paths

class Files {
    companion object {

        fun cliHomeDir(): File {
            return if (System.getenv("CORDA_CLI_HOME_DIR").isNullOrEmpty()) {
                Paths.get(System.getProperty("user.home"), "/.corda/cli/").toFile()
            } else {
                Paths.get(System.getenv("CORDA_CLI_HOME_DIR")).toFile()
            }
        }

        val profile: File by lazy {
            val profileFile = Paths.get(cliHomeDir().path, "/profile.yaml").toFile()
            if (!profileFile.exists()) {
                profileFile.createNewFile()
                profileFile.writeText("default:")
            }
            return@lazy profileFile
        }
    }
}
