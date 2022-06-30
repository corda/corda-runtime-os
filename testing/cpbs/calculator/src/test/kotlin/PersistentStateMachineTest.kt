import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.javadsl.Behaviors
import akka.persistence.testkit.javadsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import com.typesafe.config.ConfigFactory
import net.corda.testing.akka.ExamplePersistentBehavior
import net.corda.testing.akka.ExamplePersistentBehaviorCommands
import net.corda.testing.calculator.*
import org.junit.ClassRule
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
import java.util.*

@EnableRuleMigrationSupport
class PersistentStateMachineTest {

    class AuthenticatedCommunicatorImpl(
        val identity: String,
        val deliver: (String, AuthenticatedMessage) -> Unit
    ) : AuthenticatedCommunicator {
        override fun send(recipient: String, command: FlowManager.Commands) {
            deliver(recipient, authenticate(command))
        }

        override fun myIdentity(): String {
            return identity
        }

        override fun authenticate(command: FlowManager.Commands): AuthenticatedMessage {
            return AuthenticatedMessage(myIdentity(), command = command)
        }

    }

    companion object {

        fun deliver(identity: String, msg: AuthenticatedMessage) {
            if (identity == "PartyA") {
                esbTestKitA.runCommand(msg)
            } else if (identity == "PartyB") {
                esbTestKitB.runCommand(msg)
            }
        }

        val communicatorA = AuthenticatedCommunicatorImpl("PartyA", ::deliver)
        val communicatorB = AuthenticatedCommunicatorImpl("PartyB", ::deliver)

        @ClassRule
        val testKit = TestKitJunitResource(EventSourcedBehaviorTestKit.config().withFallback(ConfigFactory.defaultApplication()))
        val esbTestKitA = EventSourcedBehaviorTestKit.create<
                WireMessage,
                FlowManager.Events,
                FlowManager.State
                >(testKit.system(), Behaviors.setup {
            FlowManager(PersistenceId.ofUniqueId("1"), it, communicatorA)
        }, EventSourcedBehaviorTestKit.enabledSerializationSettings())

        val esbTestKitB = EventSourcedBehaviorTestKit.create<
                WireMessage,
                FlowManager.Events,
                FlowManager.State
                >(testKit.system(), Behaviors.setup {
            FlowManager(PersistenceId.ofUniqueId("2"), it, communicatorB)
        }, EventSourcedBehaviorTestKit.enabledSerializationSettings())
    }

    class SimpleFlowContext(val counterParty: String, val message: String): PersistentStateMachine.Context() {

        sealed class Command : SessionMessage() {
            data class SaySomething(val message: String) : Command()
            data class ProvideData(val data: String) : Command()
        }

        init {
            start {
                val sessionHandle = establishPersistentSession(ChannelIdentity.ExternalTarget(counterParty, SimpleFlowContext::class.java.name))

                sessionHandle
            }.then { session: SessionMessage.SystemMessages.SessionEstablished ->
                session.channel.send(Command.SaySomething(message))

                StateMachineEvent.WaitFor(Command.ProvideData::class.java, session)
            }.finally { data : Command.ProvideData ->
                println("Got data={${data.data}} from $counterParty")
                "Finito."
            }

         /*   onEvent(SessionMessage.SystemMessages.SessionEstablished::class.java) { msg: SessionMessage.SystemMessages.SessionEstablished ->
                StateMachineEvent.WaitFor(Command.SaySomething::class.java, msg.channel.sessionId)
            }.finally { msg: SessionMessage.SystemMessages.SessionEstablished ->
                msg.channel.send(Command.ProvideData(UUID.randomUUID().toString()))
                "Donito"
            } */

            onEvent(Command.SaySomething::class.java) { msg : EventWrapper<Command.SaySomething> ->
                println("I was told to say ${msg.event.message}")
                establishPersistentSession(msg.sender)
            }.finally { msg: SessionMessage.SystemMessages.SessionEstablished ->
                msg.channel.send(Command.ProvideData(UUID.randomUUID().toString()))
                "Donito"
            }
        }
    }

    fun `end to end test`() {

    }
}