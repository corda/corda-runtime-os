package net.corda.kryoserialization

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.io.serialization.kryo.ExternalizableKryoSerializer
import co.paralleluniverse.io.serialization.kryo.KryoSerializer
import com.esotericsoftware.kryo.ClassResolver
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.esotericsoftware.kryo.util.Pool
import net.corda.data.flow.state.checkpoint.FlowStackItem
import net.corda.internal.serialization.encoding.EncoderType
import net.corda.kryoserialization.KryoCheckpointSerializerTest.SerializableFunction
import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.serializers.ClassSerializer
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.kryoserialization.testkit.mockSandboxGroup
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointInternalCustomSerializer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.Externalizable
import java.io.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.chrono.Chronology
import java.time.zone.ZoneRules
import java.util.LinkedList
import java.util.concurrent.Executors
import java.util.function.Function

class KryoCheckpointSerializerTest {

    @Test
    fun `serialization of a simple object back a forth`() {
        val serializer = createCheckpointSerializer(
            mapOf(TestClass::class.java to TestClass.Serializer())
        )
        val tester = TestClass(TEST_INT, TEST_STRING)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    @Test
    fun `serialization of a non-serializable type throws an exception`() {
        val serializer = createCheckpointSerializer(
            mapOf(NonSerializableTestClass::class.java to NonSerializableTestClass.Serializer())
        )

        val tester = NonSerializableTestClass()

        val error = assertThrows<UnsupportedOperationException> { serializer.serialize(tester) }
        assertThat(error.message).isEqualTo(
            "net.corda.kryoserialization.NonSerializableTestClass, " +
                    "has been marked as a non-serializable type is should never be serialised into a checkpoint."
        )
    }

    @Test
    fun `serialization of an avro generated class throws an exception`() {
        val sandboxGroup = mockSandboxGroup(setOf(FlowStackItem::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver())
                    .apply {
                        // This serializer shouldn't be used
                        addDefaultSerializer(Externalizable::class.java, ExternalizableKryoSerializer<Externalizable>())
                    },
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )

        assertThatThrownBy { serializer.serialize(FlowStackItem()) }
            .isInstanceOf(UnsupportedOperationException::class.java)
            .hasMessage("${FlowStackItem::class.java.canonicalName} is an avro generated class and should never be " +
                    "serialised into a checkpoint.")
    }

    @Test
    fun `serialization of a simple object back a forth from different threads`() {
        val executor = Executors.newSingleThreadExecutor()
        val serializers: Map<Class<*>, CheckpointInternalCustomSerializer<*>> =
            mapOf(TestClass::class.java to TestClass.Serializer())

        var serializedBytes: ByteArray? = null
        // serialize in another thread
        executor.submit {
            val serializer = createCheckpointSerializer(serializers)
            val tester = TestClass(TEST_INT, TEST_STRING)
            serializedBytes = serializer.serialize(tester)
        }.get()

        // deserialize in this one
        val serializer = createCheckpointSerializer(serializers)
        val tested = serializer.deserialize(serializedBytes!!, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(TEST_INT)
        assertThat(tested.someString).isEqualTo(TEST_STRING)
    }

    @Test
    fun `serialization of a simple object using the kryo default serialization`() {
        val sandboxGroup = mockSandboxGroup(setOf(TestClass::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )
        val tester = TestClass(TEST_INT, TEST_STRING)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(tested.someInt).isEqualTo(tester.someInt)
        assertThat(tested.someString).isEqualTo(tester.someString)
    }

    private fun getQuasarKryo(classResolver: ClassResolver): Kryo {
        return (Fiber.getFiberSerializer(classResolver, false) as KryoSerializer).kryo
    }

    @Test
    fun `test java time classes`() {
        val sandboxGroup = mockSandboxGroup(emptySet())
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )
        val tester = Instant.now()

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, Instant::class.java)

        assertThat(tested)
            .isEqualTo(tester)
            .isNotSameAs(tester)
    }

    @Test
    fun `test java time chrono classes`() {
        val sandboxGroup = mockSandboxGroup(emptySet())
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )
        val tester = Chronology.getAvailableChronologies().first()

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, Chronology::class.java)

        assertThat(tested).isEqualTo(tester)
    }

    @Test
    fun `test java time zone classes`() {
        val sandboxGroup = mockSandboxGroup(emptySet())
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )
        val tester = ZoneRules.of(ZoneOffset.UTC)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, ZoneRules::class.java)

        assertThat(tested)
            .isEqualTo(tester)
            .isNotSameAs(tester)
    }

    @Test
    fun `test non-serializable lambdas`() {
        val sandboxGroup = mockSandboxGroup(setOf(this::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )

        val tester = Function<Any, String> { x -> "Hello $x, hash=${x.hashCode()}" }
        assertThatThrownBy { serializer.serialize(tester) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageStartingWith("Unable to serialize Java Lambda expression, unless explicitly declared ")
    }

    fun interface SerializableFunction<T, R> : Function<T, R>, Serializable

    data class LambdaField(val message: String)

    @Test
    fun `test serializable lambdas`() {
        val sandboxGroup = mockSandboxGroup(setOf(this::class.java, LambdaField::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )

        val obj = LambdaField("Something Extra")
        val tester = SerializableFunction<Any, String> { x -> "Hello $x, obj=$obj" }
        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, Any::class.java)

        assertThat(tested)
            .isNotSameAs(tester)
            .isInstanceOf(SerializableFunction::class.java)
        @Suppress("unchecked_cast")
        assertEquals(tester.apply("TEST"), (tested as SerializableFunction<Any, *>).apply("TEST").toString())
    }

    class TypeWithLambda(private val function: SerializableFunction<Any, String>) {
        fun apply(obj: Any) = function.apply(obj)
    }

    @Test
    fun `test object with serializable lambda`() {
        val sandboxGroup = mockSandboxGroup(setOf(this::class.java, TypeWithLambda::class.java, LambdaField::class.java))
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                kryo = getQuasarKryo(CordaClassResolver(sandboxGroup)),
                serializers = emptyMap(),
                classSerializer = ClassSerializer(sandboxGroup)
            ).toPool()
        )

        val obj = LambdaField("Something Extra")
        val tester = TypeWithLambda { x -> "Hello $x, obj=$obj" }
        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, TypeWithLambda::class.java)

        assertThat(tested)
            .isNotNull()
            .isNotSameAs(tester)
        assertEquals(tester.apply("TEST"), tested.apply("TEST"))
    }

    class ChildOfArrayList<T>(size: Int) : ArrayList<T>(size) {
        // Kryo needs a no-arg constructor.
        constructor() : this(0)
    }

    @Test
    fun `ChildOfArrayList iterator can checkpoint without error`() {
        runTestWithCollection(ChildOfArrayList())
    }

    @Test
    fun `ArrayList iterator can checkpoint without error`() {
        runTestWithCollection(ArrayList())
    }

    @Test
    fun `HashSet iterator can checkpoint without error`() {
        runTestWithCollection(HashSet())
    }

    @Test
    fun `LinkedHashSet iterator can checkpoint without error`() {
        runTestWithCollection(LinkedHashSet())
    }

    @Test
    fun `HashMap iterator can checkpoint without error`() {
        runTestWithCollection(HashMap())
    }

    @Test
    fun `LinkedHashMap iterator can checkpoint without error`() {
        runTestWithCollection(LinkedHashMap())
    }

    @Test
    fun `LinkedList iterator can checkpoint without error`() {
        runTestWithCollection(LinkedList())
    }

    private data class TestClassWithIterator<C, I>(val list: C, val iterator: I)

    private fun runTestWithCollection(collection: MutableCollection<Int>) {

        val sandboxGroup = mockSandboxGroup()
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )

        for (i in 1..20) {
            collection.add(i)
        }
        for (i in 16 until 20) {
            collection.remove(i)
        }

        val iterator = collection.iterator()
        iterator.next()

        val tester = TestClassWithIterator(collection, iterator)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, tester.javaClass)

        assertThat(tested.list).isEqualTo(collection)
        assertThat(tested.iterator.next()).isEqualTo(2)
        assertThat(tested.iterator.hasNext()).isTrue


        val uncompressedSerializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool(),
            streamEncoderType = EncoderType.NOOP
        )
        val uncompressedBytes = uncompressedSerializer.serialize(tester)
        // Assume we should get at least 20% back - conservative on purpose
        // We do the assert here on collections, which are hundreds of bytes, as _very_ small objects don't compress
        assertThat(uncompressedBytes.size * 0.8).isGreaterThan(bytes.size * 1.0)
    }

    private fun runTestWithCollection(collection: MutableMap<Int, Int>) {

        val sandboxGroup = mockSandboxGroup()
        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )

        for (i in 1..20) {
            collection[i] = i
        }
        for (i in 16 until 20) {
            collection.remove(i)
        }

        val iterator = collection.iterator()
        iterator.next()

        val tester = TestClassWithIterator(collection, iterator)

        val bytes = serializer.serialize(tester)
        val tested = serializer.deserialize(bytes, tester.javaClass)

        assertThat(tested.list).isEqualTo(collection)
        assertThat(tested.iterator.next().key).isEqualTo(2)
        assertThat(tested.iterator.hasNext()).isTrue
    }

    private fun mockSandboxGroup(): SandboxGroup = mock<SandboxGroup>().apply {
        val tagCaptor = argumentCaptor<Class<*>>()
        whenever(getStaticTag(tagCaptor.capture())).thenAnswer {
            tagCaptor.lastValue.typeName
        }
        val classCaptor = argumentCaptor<String>()
        whenever(getClass(any(), classCaptor.capture())).thenAnswer {
            Class.forName(classCaptor.lastValue)
        }
    }

    private fun Kryo.toPool(): Pool<Kryo> {
        return object : Pool<Kryo>(true, false, 4) {
            override fun create(): Kryo {
                return this@toPool
            }
        }
    }
}
