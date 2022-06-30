import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import net.corda.testing.calculator.FlowManager
import net.corda.testing.calculator.SessionMessage
import net.corda.testing.calculator.WireMessage
import net.corda.testing.calculator.system.BidirectionalSession
import net.corda.testing.calculator.system.LocalPipe
import net.corda.testing.calculator.system.SessionMessageID
import org.junit.ClassRule
import org.junit.jupiter.api.Test
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
import kotlin.concurrent.thread


class TestStringMessage(val msg: String) : SessionMessage()

@EnableRuleMigrationSupport
class BidirectionalSessionTest {

    @ClassRule
    val testKit = TestKitJunitResource(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.defaultApplication()))


    @Test
    fun `Bidirectional session must work`() {

        fun tellLocalActorA(arg: LocalPipe) {
            if (arg is LocalPipe.Deliver)  println("received ${arg.msg.payload}")

            if (arg is LocalPipe.Deliver) {
                if (arg.msg.payload is BidirectionalSession.Commands.Reconcile.Response) {
                    println("Reconcile response ${arg.msg.payload}")
                }
            }
        }

        lateinit var systemB: EventSourcedBehaviorTestKit<BidirectionalSession.Commands, BidirectionalSession.Events, BidirectionalSession.State>


        fun tellRemoteB(arg: BidirectionalSession.Commands) {
            thread {
                systemB.runCommand(arg)
            }
        }

        fun tellLocalActorB(arg: LocalPipe) {
            if (arg is LocalPipe.Deliver)  println("received ${arg.msg.payload}")

            if (arg is LocalPipe.Deliver) {
                if (arg.msg.payload is BidirectionalSession.Commands.Reconcile.Response) {
                    println("Reconcile response ${arg.msg.payload}")
                    tellRemoteB(BidirectionalSession.Commands.Reconcile.Pull((arg.msg.payload as BidirectionalSession.Commands.Reconcile.Response).queueDiff))
                }
            }
        }

        val systemA = EventSourcedBehaviorTestKit.create<
                BidirectionalSession.Commands,
                BidirectionalSession.Events,
                BidirectionalSession.State,
                >(testKit.system(), Behaviors.setup {
            BidirectionalSession("5", ::tellLocalActorA, ::tellRemoteB)
        }, EventSourcedBehaviorTestKit.enabledSerializationSettings())

        fun tellRemoteA(arg: BidirectionalSession.Commands) {
            thread {
                systemA.runCommand(arg)
            }
        }

        systemB = EventSourcedBehaviorTestKit.create(testKit.system(), Behaviors.setup {
            BidirectionalSession("6", ::tellLocalActorB, ::tellRemoteA)
        }, EventSourcedBehaviorTestKit.enabledSerializationSettings())

        systemA.runCommand(BidirectionalSession.Commands.SendMessage(BidirectionalSession.QueuedMessage(SessionMessageID("55"), TestStringMessage("Hello"))))
        Thread.sleep(5000)
        systemB.restart()
        systemB.runCommand(BidirectionalSession.Commands.Reconcile.Request(listOf(), true))
    }
}