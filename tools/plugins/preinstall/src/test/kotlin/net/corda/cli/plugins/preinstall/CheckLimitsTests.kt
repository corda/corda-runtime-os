package net.corda.cli.plugins.preinstall

//import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized
//import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized
//import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine

@CommandLine.Command(name = "sub-command", description = ["Example subcommand."])
class CheckLimitsTests{
    @Test
    fun testSubCommand() {
        println("EWQRQWER")
//        val app = PreInstallPlugin.PreInstallPluginEntry()
//        val outText = tapSystemOutNormalized {
//            CommandLine(
//                app
//            ).execute("check-limits /home/jacob/corda/corda-cli-plugin-host/resources.yaml")
//        }
//        println(outText)

//        assertTrue(outText.contains("[INFO] All resource requests are appropriate and are under the set limits."))
    }
}