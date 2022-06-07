import net.corda.testing.akka.Environment
import org.junit.jupiter.api.Test

class TestingTest {


    @Test
    fun `doing something`() {
        val example = Environment()
        example.nodes[0].issueTokens("PartyB", "PartyC", 25)
        Thread.sleep(10000)
    }
}