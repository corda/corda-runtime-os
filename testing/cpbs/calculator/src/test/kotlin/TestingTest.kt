import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import net.corda.testing.akka.Environment
import net.corda.testing.akka.ExamplePersistentBehavior
import net.corda.testing.akka.ExamplePersistentBehaviorCommands
import org.junit.ClassRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport

@EnableRuleMigrationSupport
class TestingTest {

    companion object {
        @ClassRule
        val testKit = TestKitJunitResource(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.defaultApplication()))
        val eventSourcedBehaviorTestKit = EventSourcedBehaviorTestKit.create<
                ExamplePersistentBehaviorCommands,
                ExamplePersistentBehavior.Events,
                ExamplePersistentBehavior.State
                >(testKit.system(), ExamplePersistentBehavior.create(PersistenceId.ofUniqueId("55")), EventSourcedBehaviorTestKit.enabledSerializationSettings())
    }

    @Test
    fun `doing something`() {
        val example = Environment()
        example.nodes[0].issueTokens("PartyB", "PartyC", 25)
        Thread.sleep(10000)
    }

    @Test
    fun `doing something persistent`() {
        println("Sanity log")
        run {
            eventSourcedBehaviorTestKit.runCommand(ExamplePersistentBehaviorCommands.ChangeTo("{BEEP}"))
            eventSourcedBehaviorTestKit.runCommand(ExamplePersistentBehaviorCommands.ChangeTo("{B00P}"))

            eventSourcedBehaviorTestKit.restart()
            Thread.sleep(3000)


            eventSourcedBehaviorTestKit.runCommand(ExamplePersistentBehaviorCommands.ChangeTo("{B00P}"))
            Thread.sleep(3000)

        }
    }

/*    @Test
    fun `again`() {
        run {
            val system = akka.actor.ActorSystem.create("testSystem")
            val ref = system.actorOf(Props.create(AnotherExamplePersistence::class.java, "33"))
            ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{BEEP}"), ActorRef.noSender())
            ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{B00P}"), ActorRef.noSender())
            Thread.sleep(3000)
        }
        run {
            val system = akka.actor.ActorSystem.create("testSystem")
            val ref = system.actorOf(Props.create(AnotherExamplePersistence::class.java, "33"))
            ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{BEEP}"), ActorRef.noSender())
            ref.tell(AnotherExamplePersistence.Commands.ChangeTo("{B00P}"), ActorRef.noSender())
            Thread.sleep(3000)
        }
    } */
}