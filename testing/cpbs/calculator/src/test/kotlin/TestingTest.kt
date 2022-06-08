import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.dispatch.ExecutorServiceFactory
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import net.corda.testing.akka.Environment
import net.corda.testing.akka.ExamplePersistentBehavior
import net.corda.testing.akka.ExamplePersistentBehaviorCommands
import net.corda.testing.calculator.PersistentStateMachine
import net.corda.v5.base.util.uncheckedCast
import org.junit.ClassRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

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

    class ResponderProcess(val event: String, val future: CompletableFuture<String>) {
        val t = thread {
            EventFlow().apply {
                process(null, event)
                Thread.sleep(3500)
                process(null, event)
                future.complete(uncheckedCast(this.computedResult))
            }
        }
    }

    class EventFlow : PersistentStateMachine.Context() {

        var msg : String? = null

        init {
            start {
                val uuid = UUID.randomUUID().toString()


                val res = CompletableFuture<String>()
                ResponderProcess(uuid, res)
                res
            }.then { externalMsg: String ->
                msg = externalMsg
                println(msg)

                val res = CompletableFuture<Int>()
                thread {
                    res.complete(SecureRandom.getInstanceStrong().nextInt())
                }
                res
            }.finally { rng : Int ->
                println("Rng: $rng")
                Pair(msg, rng)
            }

            onEvent(String::class.java) { uuid : String ->
                val res = CompletableFuture<String>()
                thread {
                    Thread.sleep(2000)
                    res.complete("${uuid}=>${UUID.randomUUID()}")
                }
                res
            }.finally { computed: String ->
                "Result: $computed"
            }
        }
    }

    @Test
    fun `execution context test`() {
        val flow = EventFlow()

        flow.process(null)
        Thread.sleep(3500)
        flow.process(null)
        Thread.sleep(3500)
        flow.process(null)
        println(flow.computedResult)
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