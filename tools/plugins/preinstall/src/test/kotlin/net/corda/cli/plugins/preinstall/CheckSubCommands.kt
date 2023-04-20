package net.corda.cli.plugins.preinstall

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine


class CheckSubCommands {
    @Test
    fun testNoFile() {
        val limitsCMD = CommandLine(CheckLimits())
        var outText = tapSystemOutNormalized { limitsCMD.execute("this-file-does-not-exist", "-v") }
        assertTrue( outText.contains("[ERROR] File does not exist") )

        val postgresCMD = CommandLine(CheckPostgres())
        outText = tapSystemOutNormalized { postgresCMD.execute("this-file-does-not-exist", "-nnamespace") }
        assertTrue( outText.contains("[ERROR] File does not exist") )

        val kafkaCMD = CommandLine(CheckKafka())
        outText = tapSystemOutNormalized { kafkaCMD.execute("this-file-does-not-exist", "-nnamespace", "-ftruststore.jks") }
        assertTrue( outText.contains("[ERROR] File does not exist") )
    }

    @Test
    fun testLimitsParser() {
        var path = "./src/test/resources/LimitsTest0.yaml"
        val limitsCMD = CommandLine(CheckLimits())

        var outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[INFO] All resource requests are appropriate and are under the set limits.") )

        path = "./src/test/resources/LimitsTest1.yaml"

        outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[ERROR] Resource requests for resources have been exceeded!") )

        path = "./src/test/resources/LimitsTest2.yaml"

        outText = tapSystemOutNormalized { limitsCMD.execute(path) }
        assertTrue( outText.contains("[ERROR] Invalid memory string format:") )

    }
}