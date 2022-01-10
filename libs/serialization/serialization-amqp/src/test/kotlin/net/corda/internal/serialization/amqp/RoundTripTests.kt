package net.corda.internal.serialization.amqp

import net.corda.v5.base.annotations.SerializableCalculatedProperty
import net.corda.v5.serialization.annotations.ConstructorForDeserialization
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class RoundTripTests {

    @Test
	fun mutableBecomesImmutable() {
        @CordaSerializable
        data class C(val l: MutableList<String>)

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        assertThatThrownBy {
            newC.l.add("d")
        }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
	fun mutableStillMutable() {
        @CordaSerializable
        class C(l: MutableList<String>) {
            val l: MutableList<String> = l.toMutableList()
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
        assertThat(newC.l).containsExactly("a", "b", "c", "d")
    }

    @Test
	fun mutableStillMutable2() {
        @CordaSerializable
        data class C(val l: MutableList<String>) {
            @ConstructorForDeserialization
            @Suppress("Unused")
            constructor (l: Collection<String>) : this(l.toMutableList())
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
        assertThat(newC.l).containsExactly("a", "b", "c", "d")
    }

    @Test
	fun mutableBecomesImmutable4() {
        @CordaSerializable
        data class C(val l: List<String>)

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(listOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)
        newC.copy(l = (newC.l + "d"))
    }

    @Test
	fun calculatedValues() {
        @CordaSerializable
        data class C(val i: Int) {
            @get:SerializableCalculatedProperty
            val squared = i * i
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(2))
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertThat(deserialized.squared).isEqualTo(4)
    }

    @Test
	fun calculatedFunction() {
        @CordaSerializable
        class C {
            var i: Int = 0
            @SerializableCalculatedProperty
            fun getSquared() = i * i
        }

        val instance = C().apply { i = 2 }
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertThat(deserialized.getSquared()).isEqualTo(4)
    }

    interface I {
        @get:SerializableCalculatedProperty
        val squared: Int
    }

    @Test
	fun inheritedCalculatedFunction() {
        @CordaSerializable
        class C: I {
            var i: Int = 0
            override val squared get() = i * i
        }

        val instance = C().apply { i = 2 }
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes) as I
        assertThat(deserialized.squared).isEqualTo(4)
    }

    @Test
    fun inheritedCalculatedFunctionIsNotCalculated() {
        @CordaSerializable
        class C(override val squared: Int) : I

        val instance = C(2)
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes) as I
        assertThat(deserialized.squared).isEqualTo(2)
    }

    @CordaSerializable
    data class MembershipState<out T : Any>(val metadata: T) : TestContractStateInterface {
        override val participants: List<TestParty>
            get() = emptyList()
    }

    @CordaSerializable
    data class TestStateAndRef<out T : TestContractStateInterface>(val state: TestTransactionState<T>, val ref: String)

    @CordaSerializable
    data class OnMembershipChanged(val changedMembership: TestStateAndRef<MembershipState<Any>>)

    @Test
    fun canSerializeClassesWithUntypedProperties() {
        val data = MembershipState<Any>(mapOf("foo" to "bar"))
        val transactionState = TestTransactionState(
                data,
                TestAttachmentConstraint()
        )
        val ref = "fooBar"
        val instance = OnMembershipChanged(TestStateAndRef(
                transactionState,
                ref
        ))

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertEquals(mapOf("foo" to "bar"), deserialized.changedMembership.state.data.metadata)
    }

    @CordaSerializable
    interface I2<T> {
        val t: T
    }

    @CordaSerializable
    data class C<A, B : A>(override val t: B) : I2<B>

    @Test
	fun recursiveTypeVariableResolution() {
        val factory = testDefaultFactoryNoEvolution()
        val instance = C<Collection<String>, List<String>>(emptyList())

        val bytes = SerializationOutput(factory).serialize(instance)
        DeserializationInput(factory).deserialize(bytes)

        assertEquals(
                """
                C (erased)(t: *): I2<*>
                  t: *
                """.trimIndent(),
                factory.getTypeInformation(instance::class.java).prettyPrint())
    }
}
