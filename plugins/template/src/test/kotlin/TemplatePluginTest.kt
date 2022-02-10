import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized
import net.corda.cli.plugins.examples.TemplatePlugin
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import picocli.CommandLine

class TemplatePluginTest {
    @Test
    fun testNoOptionCommand() {

        val app = TemplatePlugin.TemplatePluginEntry()

        val outText = tapSystemErrNormalized {
            CommandLine(
                app
            ).execute("")
        }

        assertTrue(
            outText.contains(
                "Usage: template\nEmpty template plugin."
            )
        )
    }
}