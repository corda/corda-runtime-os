import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.typed.ActorSystem
import akka.persistence.typed.PersistenceId
import net.corda.testing.akka.AnotherExamplePersistence
import net.corda.testing.akka.Environment
import net.corda.testing.akka.ExamplePersistentBehavior
import org.junit.jupiter.api.Test

class TestingTest {


    @Test
    fun `doing something`() {
        val example = Environment()
        example.nodes[0].issueTokens("PartyB", "PartyC", 25)
        Thread.sleep(10000)
    }

    @Test
    fun `doing something persistent`() {
        println("Sanity log")
        val system = ActorSystem.create(ExamplePersistentBehavior.create(PersistenceId.ofUniqueId("55")), "testSystem")
        system.tell(ExamplePersistentBehavior.Commands.ChangeTo("{BEEP}"))
        system.tell(ExamplePersistentBehavior.Commands.ChangeTo("{B00P}"))
        Thread.sleep(10000)
    }

    @Test
    fun `again`() {
        val system = akka.actor.ActorSystem.create("testSystem")
        val ref = system.actorOf(Props.create(AnotherExamplePersistence::class.java, "33"))
        ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{BEEP}"), ActorRef.noSender())
        ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{B00P}"), ActorRef.noSender())
        Thread.sleep(10000)
    }
}