import akka.actor.ActorSystem
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import net.corda.testing.akka.Environment
import net.corda.testing.akka.ExamplePersistentBehavior
import net.corda.testing.akka.ExamplePersistentBehaviorCommands
import net.corda.testing.calculator.ChannelIdentity
import net.corda.testing.calculator.PersistentStateMachine
import net.corda.v5.base.util.uncheckedCast
import org.junit.ClassRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

@EnableRuleMigrationSupport
class TestingTest {

    fun smh() {
        ActorSystem.create("hehe").let {

        }
    }

/*
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
    fun `persistent flows`() {

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

   /* class ResponderProcess(val event: String, val future: CompletableFuture<String>) {
        val t = thread {
            EventFlow(null).apply {
                process(null, event)
                Thread.sleep(3500)
                process(null, event)
                future.complete(uncheckedCast(this.computedResult))
            }
        }
    } */

    class EventFlow(val testEnvSpawnResponder: ((String)->String)?) : PersistentStateMachine.Context() {
        var selectedAsset : String? = null
        object AssetAvailable

        init {
            start {
                val uuid = UUID.randomUUID().toString()
                val sessionId = testEnvSpawnResponder!!.invoke(uuid)

                //establishPersistentSession("PartyA")
                return@start StateMachineEvent.WaitFor(String::class.java, sessionId)

            }.then { assetTypeID: String ->

                if (!SecureRandom.getInstanceStrong().nextBoolean()) {
                    println("Our external system is unable to respond for asset type ID: { $assetTypeID } right now;")
                    return@then StateMachineEvent.Cancel(AssetAvailable::class.java) //Wait for signal from another actor to retry this stage
                }

                val totallyNotRandomNotFictivePrice = SecureRandom.getInstanceStrong().nextInt(42)

                if (totallyNotRandomNotFictivePrice < 4) {
                    println("This asset price sucks! Going back to find something more suitable.")
                    return@then StateMachineEvent.RevertTo(0)
                }

                selectedAsset = assetTypeID
                println(selectedAsset)

                StateMachineEvent.ProceedNow(totallyNotRandomNotFictivePrice)
            }.finally { rng : Int ->
                println("Rng: $rng")
                Pair(selectedAsset, rng)
            }

            onEvent(String::class.java) { uuid : String ->
                Thread.sleep(2000)
                StateMachineEvent.ProceedNow("${uuid}=>${UUID.randomUUID()}")
            }.finally { computed: String ->
                "Result: $computed"
            }

        }
    }

    @Test
    fun `execution context test`() {

        val responderResult = CompletableFuture<String>()
        fun spawnResponder(uuid: String): String {
            thread {
                Thread.sleep(3500)

                EventFlow(null).let { flowStateMachine ->
                    val res : PersistentStateMachine.Context.StateMachineEvent.ProceedNow = uncheckedCast(flowStateMachine.start(uuid))
                    val finish: PersistentStateMachine.Context.StateMachineEvent.Finish = uncheckedCast(flowStateMachine.process(
                        uncheckedCast(res.eventForNextStep)))
                    responderResult.complete(uncheckedCast(finish.result))
                }
            }
            val sessionID = "${UUID.randomUUID()}"
            return sessionID
        }

        val flow = EventFlow(::spawnResponder)

        val waitFor : PersistentStateMachine.Context.StateMachineEvent.WaitFor = uncheckedCast(flow.start(UUID.randomUUID().toString()))
        var res = flow.process(UUID.randomUUID().toString(), responderResult.get(), waitFor.from)
        while (res !is PersistentStateMachine.Context.StateMachineEvent.ProceedNow) {
            //Wont work for revert to, but can't be bothered to mock it in a test
            res = flow.process(UUID.randomUUID().toString(), responderResult.get(), waitFor.from)
        }

        val finishResult : PersistentStateMachine.Context.StateMachineEvent.Finish = uncheckedCast(flow.process(UUID.randomUUID().toString(), res.eventForNextStep))
        println(finishResult)
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
} */
}