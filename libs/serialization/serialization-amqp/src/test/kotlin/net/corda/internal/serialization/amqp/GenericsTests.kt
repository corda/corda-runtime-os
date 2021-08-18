package net.corda.internal.serialization.amqp

import net.corda.v5.serialization.SerializedBytes
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.testutils.ProjectStructure.projectRootDir
import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class TestTransaction(val bob: String)

interface TestContract {
    fun verify(tx: TestTransaction)
}

abstract class TestParty(val uncle: Int)

class ConcreteTestParty(uncle: Int) : TestParty(uncle)

val MINI_CORP_PARTY = ConcreteTestParty(1)

interface TestContractStateInterface {
    val participants: List<TestParty>
}

data class TestTransactionState<out T : TestContractStateInterface> constructor(
        val data: T,
        val constraint: AttachmentConstraintInterface)

open class TestContractState(
        override val participants: List<TestParty>
) : TestContractStateInterface

interface Attachment

interface AttachmentConstraintInterface {
    fun isSatisfiedBy(attachment: Attachment): Boolean
}

class TestAttachmentConstraint : AttachmentConstraintInterface {
    override fun isSatisfiedBy(attachment: Attachment) = true
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GenericsTests {
    companion object {
        const val VERBOSE = true

        @Suppress("UNUSED")
        var localPath: URI = projectRootDir.toUri().resolve(
                "serialization-internal/src/test/resources/net/corda/internal/serialization/amqp")
    }

    private fun printSeparator() = if (VERBOSE) println("\n\n-------------------------------------------\n\n") else Unit

    private fun <T : Any> BytesAndSchemas<T>.printSchema() = if (VERBOSE) println("${this.schema}\n") else Unit

    @Test
	fun twoDifferentTypesSameParameterizedOuter() {
        data class G<A>(val a: A)

        val factory = testDefaultFactoryNoEvolution()

        val bytes1 = SerializationOutput(factory).serializeAndReturnSchema(G("hi")).apply { printSchema() }

        val bytes2 = SerializationOutput(factory).serializeAndReturnSchema(G(121)).apply { printSchema() }

        listOf(factory, testDefaultFactory()).forEach { f ->
            DeserializationInput(f).deserialize(bytes1.obj).apply { assertEquals("hi", this.a) }
            DeserializationInput(f).deserialize(bytes2.obj).apply { assertEquals(121, this.a) }
        }
    }

    @Test
	fun doWeIgnoreMultipleParams() {
        data class G1<out T>(val a: T)
        data class G2<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G1<T>, val c: G2<T>)

        val factory = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactoryNoEvolution()

        val bytes = SerializationOutput(factory).serializeAndReturnSchema(Wrapper(1, G1("hi"), G2("poop"))).apply { printSchema() }
        printSeparator()
        DeserializationInput(factory2).deserialize(bytes.obj)
    }

    @Test
	fun nestedSerializationOfGenerics() {
        data class G<out T>(val a: T)
        data class Wrapper<out T>(val a: Int, val b: G<T>)

        val factory = testDefaultFactoryNoEvolution()
        val altContextFactory = testDefaultFactoryNoEvolution()
        val ser = SerializationOutput(factory)

        val bytes = ser.serializeAndReturnSchema(G("hi")).apply { printSchema() }

        assertEquals("hi", DeserializationInput(factory).deserialize(bytes.obj).a)
        assertEquals("hi", DeserializationInput(altContextFactory).deserialize(bytes.obj).a)

        val bytes2 = ser.serializeAndReturnSchema(Wrapper(1, G("hi"))).apply { printSchema() }

        printSeparator()

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }

        DeserializationInput(altContextFactory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals("hi", b.a)
        }
    }

    @Test
	fun nestedGenericsReferencesByteArrayViaSerializedBytes() {
        data class G(val a: Int)
        data class Wrapper<T : Any>(val a: Int, val b: SerializedBytes<T>)

        val factory = SerializerFactoryBuilder.build(AllWhitelist)
        val factory2 = SerializerFactoryBuilder.build(AllWhitelist)
        val ser = SerializationOutput(factory)

        val gBytes = ser.serialize(G(1))
        val bytes2 = ser.serializeAndReturnSchema(Wrapper<G>(1, gBytes))

        DeserializationInput(factory).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
        DeserializationInput(factory2).deserialize(bytes2.obj).apply {
            assertEquals(1, a)
            assertEquals(1, DeserializationInput(factory).deserialize(b).a)
        }
    }

    @Test
	fun nestedSerializationInMultipleContextsDoesntColideGenericTypes() {
        data class InnerA(val a_a: Int)
        data class InnerB(val a_b: Int)
        data class InnerC(val a_c: String)
        data class Container<T>(val b: T)
        data class Wrapper<T : Any>(val c: Container<T>)

        val factory = SerializerFactoryBuilder.build(AllWhitelist)
        val factories = listOf(factory, SerializerFactoryBuilder.build(AllWhitelist))
        val ser = SerializationOutput(factory)

        ser.serialize(Wrapper(Container(InnerA(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_a) }
            }
        }

        ser.serialize(Wrapper(Container(InnerB(1)))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals(1, c.b.a_b) }
            }
        }

        ser.serialize(Wrapper(Container(InnerC("Ho ho ho")))).apply {
            factories.forEach {
                DeserializationInput(it).deserialize(this).apply { assertEquals("Ho ho ho", c.b.a_c) }
            }
        }
    }

    @Test
	fun nestedSerializationWhereGenericDoesntImpactFingerprint() {
        data class Inner(val a: Int)
        data class Container<T : Any>(val b: Inner)
        data class Wrapper<T : Any>(val c: Container<T>)

        val factorys = listOf(
                SerializerFactoryBuilder.build(AllWhitelist),
                SerializerFactoryBuilder.build(AllWhitelist)
        )

        val ser = SerializationOutput(factorys[0])

        ser.serialize(Wrapper<Int>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }

        ser.serialize(Wrapper<String>(Container(Inner(1)))).apply {
            factorys.forEach {
                assertEquals(1, DeserializationInput(it).deserialize(this).c.b.a)
            }
        }
    }

    data class ForceWildcard<out T>(val t: T)

    private fun forceWildcardSerialize(
            a: ForceWildcard<*>,
            factory: SerializerFactory = SerializerFactoryBuilder.build(AllWhitelist)
    ): SerializedBytes<*> {
        val bytes = SerializationOutput(factory).serializeAndReturnSchema(a)
        bytes.printSchema()
        return bytes.obj
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserializeString(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactoryBuilder.build(AllWhitelist)
    ) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<String>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserializeDouble(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactoryBuilder.build(AllWhitelist)
    ) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<Double>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun forceWildcardDeserialize(
            bytes: SerializedBytes<*>,
            factory: SerializerFactory = SerializerFactoryBuilder.build(AllWhitelist)
    ) {
        DeserializationInput(factory).deserialize(bytes as SerializedBytes<ForceWildcard<*>>)
    }

    @Test
	fun forceWildcard() {
        forceWildcardDeserializeString(forceWildcardSerialize(ForceWildcard("hello")))
        forceWildcardDeserializeDouble(forceWildcardSerialize(ForceWildcard(3.0)))
    }

    @Test
	fun forceWildcardSharedFactory() {
        val f = SerializerFactoryBuilder.build(AllWhitelist)
        forceWildcardDeserializeString(forceWildcardSerialize(ForceWildcard("hello"), f), f)
        forceWildcardDeserializeDouble(forceWildcardSerialize(ForceWildcard(3.0), f), f)
    }

    @Test
	fun forceWildcardDeserialize() {
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard("hello")))
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(10)))
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(20.0)))
    }

    @Test
	fun forceWildcardDeserializeSharedFactory() {
        val f = SerializerFactoryBuilder.build(AllWhitelist)
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard("hello"), f), f)
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(10), f), f)
        forceWildcardDeserialize(forceWildcardSerialize(ForceWildcard(20.0), f), f)
    }

    @Test
	fun loadGenericFromFile() {
        val resource = "${javaClass.simpleName}.${testName()}"
        val sf = testDefaultFactory()

        // Uncomment to re-generate test files
        // File(URI("$localPath/$resource")).writeBytes(forceWildcardSerialize(ForceWildcard("wibble")).bytes)

        assertEquals("wibble",
                DeserializationInput(sf).deserialize(SerializedBytes<ForceWildcard<*>>(
                        GenericsTests::class.java.getResource(resource).readBytes())).t)
    }

    data class StateAndString(val state: TestTransactionState<*>, val ref: String)
    data class GenericStateAndString<out T : TestContractState>(val state: TestTransactionState<T>, val ref: String)

    //
    // If this doesn't blow up all is fine
    private fun fingerprintingDiffersStrip(state: Any) {
        class cl : ClassLoader()

        val m = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
        m.isAccessible = true

        val factory1 = testDefaultFactory()
        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(state)

        // attempt at having a class loader without some of the derived non core types loaded and thus
        // possibly altering how we serialise things

        val factory2 = SerializerFactoryBuilder.build(AllWhitelist)
        val ser2 = TestSerializationOutput(VERBOSE, factory2).serializeAndReturnSchema(state)

        //  now deserialise those objects
        val factory3 = testDefaultFactory()
        DeserializationInput(factory3).deserializeAndReturnEnvelope(ser1.obj)

        val factory4 = SerializerFactoryBuilder.build(AllWhitelist)
        DeserializationInput(factory4).deserializeAndReturnEnvelope(ser2.obj)
    }

    @Test
	fun fingerprintingDiffers() {
        val state = TestTransactionState(
                TestContractState(listOf(MINI_CORP_PARTY)),
                constraint = TestAttachmentConstraint())

        val sas = StateAndString(state, "wibble")

        fingerprintingDiffersStrip(sas)
    }

    @Test
	fun fingerprintingDiffersList() {
        val state = TestTransactionState(
                TestContractState(listOf(MINI_CORP_PARTY)),
                constraint = TestAttachmentConstraint())

        val sas = StateAndString(state, "wibble")

        fingerprintingDiffersStrip(Collections.singletonList(sas))
    }


    //
    // Force object to be serialised as Example<T> and deserialized as Example<?>
    //
    @Test
	fun fingerprintingDiffersListLoaded() {
        //
        // using this wrapper class we force the object to be serialised as
        //      net.corda.core.contracts.TransactionState<T>
        //
        data class TransactionStateWrapper<out T : TestContractState>(val o: List<GenericStateAndString<T>>)

        val state = TestTransactionState<TestContractState>(
                TestContractState(listOf(MINI_CORP_PARTY)),
                constraint = TestAttachmentConstraint())

        val sas = GenericStateAndString(state, "wibble")

        val factory1 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(
                TransactionStateWrapper(Collections.singletonList(sas)))

        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(sas.ref, des1.obj.o.firstOrNull()?.ref ?: "WILL NOT MATCH")
    }

    @Test
	fun nestedGenericsWithBound() {
        open class BaseState(val a : Int)
        class DState(a: Int) : BaseState(a)
        data class LTransactionState<out T : BaseState> constructor(val data: T)
        data class StateWrapper<out T : BaseState>(val state: LTransactionState<T>)

        val factory1 = testDefaultFactoryNoEvolution()

        val state = LTransactionState(DState(1020304))
        val stateAndString = StateWrapper(state)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(stateAndString)

        //val factory2 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.data.a, des1.obj.state.data.a)
    }

    @Test
	fun nestedMultiGenericsWithBound() {
        open class BaseState(val a : Int)
        class DState(a: Int) : BaseState(a)
        class EState(a: Int, val msg: String) : BaseState(a)

        data class LTransactionState<out T1 : BaseState, out T2: BaseState> (val data: T1, val context: T2)
        data class StateWrapper<out T1 : BaseState, out T2: BaseState>(val state: LTransactionState<T1, T2>)

        val factory1 = testDefaultFactoryNoEvolution()

        val state = LTransactionState(DState(1020304), EState(5060708, msg = "thigns"))
        val stateAndString = StateWrapper(state)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(stateAndString)

        //val factory2 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.data.a, des1.obj.state.data.a)
        assertEquals(state.context.a,  des1.obj.state.context.a)
    }

    @Test
	fun nestedMultiGenericsNoBound() {
        open class BaseState(val a : Int)
        class DState(a: Int) : BaseState(a)
        class EState(a: Int, val msg: String) : BaseState(a)

        data class LTransactionState<out T1, out T2> (val data: T1, val context: T2)
        data class StateWrapper<out T1, out T2>(val state: LTransactionState<T1, T2>)

        val factory1 = testDefaultFactoryNoEvolution()

        val state = LTransactionState(DState(1020304), EState(5060708, msg = "things"))
        val stateAndString = StateWrapper(state)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(stateAndString)

        //val factory2 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.data.a, des1.obj.state.data.a)
        assertEquals(state.context.a, des1.obj.state.context.a)
        assertEquals(state.context.msg, des1.obj.state.context.msg)
    }

    @Test
	fun baseClassInheritedButNotOverriden() {
        val factory1 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()

        open class BaseState<T1, T2>(open val a : T1, open val b: T2)
        class DState<T1, T2>(a: T1, b: T2) : BaseState<T1, T2>(a, b)

        val state = DState(100, "hello")
        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(state)
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.a, des1.obj.a)
        assertEquals(state.b, des1.obj.b)

        class DState2<T1, T2, T3>(a: T1, b: T2, val c: T3) : BaseState<T1, T2>(a, b)

        val state2 = DState2(100, "hello", 100L)
        val ser2 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(state2)
        val des2 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser2.obj)

        assertEquals(state2.a, des2.obj.a)
        assertEquals(state2.b, des2.obj.b)
        assertEquals(state2.c, des2.obj.c)
    }

    @Test
	fun baseClassInheritedButNotOverridenBounded() {
        val factory1 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()

        open class Bound(val a: Int)

        open class BaseState<out T1 : Bound>(open val a: T1)
        class DState<out T1: Bound>(a: T1) : BaseState<T1>(a)

        val state = DState(Bound(100))
        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(state)
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.a.a, des1.obj.a.a)
    }

    @Test
	fun nestedMultiGenericsAtBottomWithBound() {
        open class BaseState<T1, T2>(val a : T1, val b: T2)
        class DState<T1, T2>(a: T1, b: T2) : BaseState<T1, T2>(a, b)
        class EState<T1, T2>(a: T1, b: T2, val c: Long) : BaseState<T1, T2>(a, b)

        data class LTransactionState<T1, T2, T3 : BaseState<T1, T2>, out T4: BaseState<T1, T2>> (val data: T3, val context: T4)
        data class StateWrapper<T1, T2, T3 : BaseState<T1, T2>, out T4: BaseState<T1, T2>>(val state: LTransactionState<T1, T2, T3, T4>)

        val factory1 = testDefaultFactoryNoEvolution()

        val state = LTransactionState(DState(1020304, "Hello"), EState(5060708, "thins", 100L))
        val stateAndString = StateWrapper(state)

        val ser1 = TestSerializationOutput(VERBOSE, factory1).serializeAndReturnSchema(stateAndString)

        //val factory2 = testDefaultFactoryNoEvolution()
        val factory2 = testDefaultFactory()
        val des1 = DeserializationInput(factory2).deserializeAndReturnEnvelope(ser1.obj)

        assertEquals(state.data.a, des1.obj.state.data.a)
        assertEquals(state.context.a,  des1.obj.state.context.a)
    }

    fun implemntsGeneric() {
        open class B<out T>(open val a: T)
        class D(override val a: String) : B<String>(a)

        val factory = testDefaultFactoryNoEvolution()

        val bytes = SerializationOutput(factory).serialize(D("Test"))

        DeserializationInput(factory).deserialize(bytes).apply { assertEquals("Test", this.a) }
    }

    interface implementsGenericInterfaceI<out T> {
        val a: T
    }

    @Test
	fun implemntsGenericInterface() {
        class D(override val a: String) : implementsGenericInterfaceI<String>

        val factory = testDefaultFactoryNoEvolution()

        val bytes = SerializationOutput(factory).serialize(D("Test"))

        DeserializationInput(factory).deserialize(bytes).apply { assertEquals("Test", this.a) }
    }
}
