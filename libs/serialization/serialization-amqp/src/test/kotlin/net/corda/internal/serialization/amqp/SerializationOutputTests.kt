package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.CordaSerializationEncoding
import net.corda.internal.serialization.SnappyEncodingWhitelist
import net.corda.internal.serialization.amqp.custom.BigDecimalSerializer
import net.corda.internal.serialization.amqp.custom.CurrencySerializer
import net.corda.internal.serialization.amqp.custom.InputStreamSerializer
import net.corda.internal.serialization.amqp.custom.StackTraceElementSerializer
import net.corda.internal.serialization.amqp.custom.ThrowableSerializer
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.internal.serialization.encodingNotPermittedFormat
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.serialization.EncodingWhitelist
import net.corda.serialization.SerializationContext
import net.corda.v5.application.flows.exception.FlowException
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.OpaqueBytes
import net.corda.v5.serialization.annotations.ConstructorForDeserialization
import org.apache.qpid.proton.amqp.Decimal128
import org.apache.qpid.proton.amqp.Decimal32
import org.apache.qpid.proton.amqp.Decimal64
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.amqp.UnsignedByte
import org.apache.qpid.proton.amqp.UnsignedInteger
import org.apache.qpid.proton.amqp.UnsignedLong
import org.apache.qpid.proton.amqp.UnsignedShort
import org.apache.qpid.proton.codec.DecoderImpl
import org.apache.qpid.proton.codec.EncoderImpl
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.IOException
import java.io.InputStream
import java.io.NotSerializableException
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Month
import java.util.Currency
import java.util.Date
import java.util.EnumMap
import java.util.NavigableMap
import java.util.Objects
import java.util.Random
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID

object AckWrapper {
    @CordaSerializable
    object Ack

    fun serialize() {
        val factory = testDefaultFactoryNoEvolution()
        SerializationOutput(factory).serialize(Ack)
    }
}

object PrivateAckWrapper {
    @CordaSerializable
    private object Ack

    fun serialize() {
        val factory = testDefaultFactoryNoEvolution()
        SerializationOutput(factory).serialize(Ack)
    }
}

@Timeout(30)
class SerializationOutputTests {

    @CordaSerializable
    data class Foo(val bar: String, val pub: Int)

    @CordaSerializable
    data class TestFloat(val f: Float)

    @CordaSerializable
    data class TestDouble(val d: Double)

    @CordaSerializable
    data class TestShort(val s: Short)

    @CordaSerializable
    data class TestBoolean(val b: Boolean)

    @CordaSerializable
    interface FooInterface {
        val pub: Int
    }

    @CordaSerializable
    data class FooImplements(val bar: String, override val pub: Int) : FooInterface

    @CordaSerializable
    data class FooImplementsAndList(val bar: String, override val pub: Int, val names: List<String>) : FooInterface

    @CordaSerializable
    data class WrapHashMap(val map: Map<String, String>)

    @CordaSerializable
    data class WrapFooListArray(val listArray: Array<List<Foo>>) {
        override fun equals(other: Any?): Boolean {
            return other is WrapFooListArray && Objects.deepEquals(listArray, other.listArray)
        }

        override fun hashCode(): Int {
            return 1 // This isn't used, but without overriding we get a warning.
        }
    }

    @CordaSerializable
    data class Woo(val fred: Int) {
        val bob = "Bob"
    }

    @CordaSerializable
    data class Woo2(val fred: Int, val bob: String = "Bob") {
        @ConstructorForDeserialization
        constructor(fred: Int) : this(fred, "Ginger")
    }

    @CordaSerializable
    data class AnnotatedWoo(val fred: Int) {
        val bob = "Bob"
    }

    @CordaSerializable
    class FooList : ArrayList<Foo>()

    @Suppress("AddVarianceModifier")
    @CordaSerializable
    data class GenericFoo<T>(val bar: String, val pub: T)

    @CordaSerializable
    data class ContainsGenericFoo(val contain: GenericFoo<String>)

    @CordaSerializable
    data class NestedGenericFoo<T>(val contain: GenericFoo<T>)

    @CordaSerializable
    data class ContainsNestedGenericFoo(val contain: NestedGenericFoo<String>)

    @CordaSerializable
    data class TreeMapWrapper(val tree: TreeMap<Int, Foo>)

    @CordaSerializable
    data class NavigableMapWrapper(val tree: NavigableMap<Int, Foo>)

    @CordaSerializable
    data class SortedSetWrapper(val set: SortedSet<Int>)

    @CordaSerializable
    open class InheritedGeneric<out X>(val foo: X)

    @CordaSerializable
    data class ExtendsGeneric(val bar: Int, val pub: String) : InheritedGeneric<String>(pub)

    @CordaSerializable
    interface GenericInterface<out X> {
        val pub: X
    }

    @CordaSerializable
    data class ImplementsGenericString(val bar: Int, override val pub: String) : GenericInterface<String>

    @CordaSerializable
    data class ImplementsGenericX<out Y>(val bar: Int, override val pub: Y) : GenericInterface<Y>

    @CordaSerializable
    abstract class AbstractGenericX<out Z> : GenericInterface<Z>

    @CordaSerializable
    data class InheritGenericX<out A>(val duke: Double, override val pub: A) : AbstractGenericX<A>()

    @CordaSerializable
    data class CapturesGenericX(val foo: GenericInterface<String>)

    @CordaSerializable
    object KotlinObject

    @CordaSerializable
    class Mismatch(fred: Int) {
        private val ginger: Int = fred

        override fun equals(other: Any?): Boolean = (other as? Mismatch)?.ginger == ginger
        override fun hashCode(): Int = ginger
    }

    @CordaSerializable
    class MismatchType(fred: Long) {
        private val ginger: Int = fred.toInt()

        override fun equals(other: Any?): Boolean = (other as? MismatchType)?.ginger == ginger
        override fun hashCode(): Int = ginger
    }

    @CordaSerializable
    interface AnnotatedInterface

    data class InheritAnnotation(val foo: String) : AnnotatedInterface

    @CordaSerializable
    data class PolymorphicProperty(val foo: FooInterface?)

    @CordaSerializable
    class NonZeroByte(val value: Byte) {
        init {
            require(value.toInt() != 0) { "Zero not allowed" }
        }
    }

    private val encodingWhitelist = SnappyEncodingWhitelist

    @SuppressWarnings("LongParameterList")
    private inline fun <reified T : Any> serdes(obj: T,
                                                factory: SerializerFactory = testDefaultFactoryNoEvolution(),
                                                freshDeserializationFactory: SerializerFactory = testDefaultFactoryNoEvolution(),
                                                expectedEqual: Boolean = true,
                                                expectDeserializedEqual: Boolean = true,
                                                withSerializationContext: SerializationContext = testSerializationContext): T {
        val ser = SerializationOutput(factory)
        val bytes = ser.serialize(obj, withSerializationContext)

        val decoder = DecoderImpl().apply {
            this.register(Envelope.DESCRIPTOR, Envelope)
            this.register(Metadata.DESCRIPTOR, Metadata)
            this.register(Schema.DESCRIPTOR, Schema)
            this.register(Descriptor.DESCRIPTOR, Descriptor)
            this.register(Field.DESCRIPTOR, Field)
            this.register(CompositeType.DESCRIPTOR, CompositeType)
            this.register(Choice.DESCRIPTOR, Choice)
            this.register(RestrictedType.DESCRIPTOR, RestrictedType)
            this.register(ReferencedObject.DESCRIPTOR, ReferencedObject)
            this.register(TransformsSchema.DESCRIPTOR, TransformsSchema)
            this.register(TransformTypes.DESCRIPTOR, TransformTypes)
        }
        EncoderImpl(decoder)
        DeserializationInput.withDataBytes(bytes, encodingWhitelist) {
            decoder.byteBuffer = it
            // Check that a vanilla AMQP decoder can deserialize without schema.
            val result = decoder.readObject() as Envelope
            assertNotNull(result)
        }
        val des = DeserializationInput(freshDeserializationFactory)
        val desObj = des.deserialize(bytes, withSerializationContext.withEncodingWhitelist(encodingWhitelist))
        assertTrue(deepEquals(obj, desObj) == expectedEqual)

        // Now repeat with a re-used factory
        val ser2 = SerializationOutput(factory)
        val des2 = DeserializationInput(factory)
        val desObj2 = des2.deserialize(
            ser2.serialize(obj, context = withSerializationContext),
            withSerializationContext.withEncodingWhitelist(encodingWhitelist)
        )
        assertTrue(deepEquals(obj, desObj2) == expectedEqual)
        assertTrue(deepEquals(desObj, desObj2) == expectDeserializedEqual)

        return desObj
    }

    @Test
    fun isPrimitive() {
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Character::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Boolean::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Byte::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(UnsignedByte::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Short::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(UnsignedShort::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Int::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(UnsignedInteger::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Long::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(UnsignedLong::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Float::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Double::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Decimal32::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Decimal64::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Decimal128::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Char::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Date::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(UUID::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(ByteArray::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(String::class.java))
        assertTrue(AMQPTypeIdentifiers.isPrimitive(Symbol::class.java))
    }

    @Test
    fun `test foo`() {
        val obj = Foo("Hello World!", 123)
        serdes(obj)
    }

    @Test
    fun `test float`() {
        val obj = TestFloat(10.0F)
        serdes(obj)
    }

    @Test
    fun `test double`() {
        val obj = TestDouble(10.0)
        serdes(obj)
    }

    @Test
    fun `test short`() {
        val obj = TestShort(1)
        serdes(obj)
    }

    @Test
    fun `test bool`() {
        val obj = TestBoolean(true)
        serdes(obj)
    }

    @Test
    fun `test foo implements`() {
        val obj = FooImplements("Hello World!", 123)
        serdes(obj)
    }

    @Test
    fun `test foo implements and list`() {
        val obj = FooImplementsAndList("Hello World!", 123, listOf("Fred", "Ginger"))
        serdes(obj)
    }

    @Test
    fun `test dislike of HashMap`() {
        val obj = WrapHashMap(HashMap())
        assertThrows<IllegalArgumentException> {
            serdes(obj)
        }
    }

    @Test
    fun `test string array`() {
        val obj = arrayOf("Fred", "Ginger")
        serdes(obj)
    }

    @Test
    fun `test foo array`() {
        val obj = arrayOf(Foo("Fred", 1), Foo("Ginger", 2))
        serdes(obj)
    }

    @Test
    fun `test top level list array`() {
        val obj = arrayOf(listOf("Fred", "Ginger"), listOf("Rogers", "Hammerstein"))
        serdes(obj)
    }

    @Test
    fun `test foo list array`() {
        val obj = WrapFooListArray(arrayOf(listOf(Foo("Fred", 1), Foo("Ginger", 2)),
            listOf(Foo("Rogers", 3), Foo("Hammerstein", 4))))
        serdes(obj)
    }

    @Test
    fun `test not all properties in constructor`() {
        val obj = Woo(2)
        serdes(obj)
    }

    @Test
    fun `test annotated constructor`() {
        val obj = Woo2(3)
        serdes(obj)
    }

    @Test
    fun `test annotation whitelisting`() {
        val obj = AnnotatedWoo(5)
        serdes(obj, SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup()))
    }

    @Test
    fun `test generic list subclass is not supported`() {
        val obj = FooList()
        assertThrows<NotSerializableException> {
            serdes(obj)
        }
    }

    @Test
    fun `test generic foo`() {
        val obj = GenericFoo("Fred", "Ginger")
        serdes(obj)
    }

    @Test
    fun `test generic foo as property`() {
        val obj = ContainsGenericFoo(GenericFoo("Fred", "Ginger"))
        serdes(obj)
    }

    @Test
    fun `test nested generic foo as property`() {
        val obj = ContainsNestedGenericFoo(NestedGenericFoo(GenericFoo("Fred", "Ginger")))
        serdes(obj)
    }

    @Test
    fun `test extends generic`() {
        val obj = ExtendsGeneric(1, "Ginger")
        serdes(obj)
    }

    @Test
    fun `test implements generic`() {
        val obj = ImplementsGenericString(1, "Ginger")
        serdes(obj)
    }

    @Test
    fun `test implements generic captured`() {
        val obj = CapturesGenericX(ImplementsGenericX(1, "Ginger"))
        serdes(obj)
    }

    @Test
    fun `test inherits generic captured`() {
        val obj = CapturesGenericX(InheritGenericX(1.0, "Ginger"))
        serdes(obj)
    }

    @Test
    fun `test TreeMap`() {
        val obj = TreeMap<Int, Foo>()
        obj[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test
    fun `test TreeMap property`() {
        val obj = TreeMapWrapper(TreeMap())
        obj.tree[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test
    fun `test NavigableMap property`() {
        val obj = NavigableMapWrapper(TreeMap())
        obj.tree[456] = Foo("Fred", 123)
        serdes(obj)
    }

    @Test
    fun `test SortedSet property`() {
        val obj = SortedSetWrapper(TreeSet())
        obj.set += 456
        serdes(obj)
    }

    @Test
    fun `test mismatched property and constructor naming`() {
        val obj = Mismatch(456)
        assertThrows<NotSerializableException> {
            serdes(obj)
        }
    }

    @Test
    fun `test mismatched property and constructor type`() {
        val obj = MismatchType(456)
        assertThrows<NotSerializableException> {
            serdes(obj)
        }
    }

    @Test
    fun `class constructor is invoked on deserialisation`() {
        val serializerFactory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val ser = SerializationOutput(serializerFactory)
        val des = DeserializationInput(serializerFactory)
        val serialisedOne = ser.serialize(NonZeroByte(1)).bytes
        val serialisedTwo = ser.serialize(NonZeroByte(2)).bytes

        // Find the index that holds the value byte
        val valueIndex = serialisedOne.zip(serialisedTwo).mapIndexedNotNull { index, (oneByte, twoByte) ->
            if (oneByte.toInt() == 1 && twoByte.toInt() == 2) index else null
        }.single()

        val copy = serialisedTwo.clone()

        // Double check
        copy[valueIndex] = 0x03
        assertThat(des.deserialize(OpaqueBytes(copy),
        NonZeroByte::class.java, testSerializationContext.withEncodingWhitelist(encodingWhitelist)).value).isEqualTo(3)

        // Now use the forbidden value
        copy[valueIndex] = 0x00
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            des.deserialize(OpaqueBytes(copy), NonZeroByte::class.java, testSerializationContext.withEncodingWhitelist(encodingWhitelist))
        }.withStackTraceContaining("Zero not allowed")
    }

    @Test
    fun `test annotation is inherited`() {
        val obj = InheritAnnotation("blah")
        serdes(obj, SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup()))
    }

    @Test
    fun `generics from java are supported`() {
        val obj = DummyOptional("YES")
        serdes(obj, SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup()))
    }

    @Test
    fun `test throwables serialize`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(ThrowableSerializer(factory), factory)
        factory.register(StackTraceElementSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(ThrowableSerializer(factory2), factory2)
        factory2.register(StackTraceElementSerializer(), factory2)

        val t = IllegalAccessException("message").fillInStackTrace()

        val desThrowable = serdes(t, factory, factory2, false)
        assertSerializedThrowableEquivalent(t, desThrowable)
    }

    @Test
    fun `test complex throwables serialize`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(ThrowableSerializer(factory), factory)
        factory.register(StackTraceElementSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(ThrowableSerializer(factory2), factory2)
        factory2.register(StackTraceElementSerializer(), factory2)

        try {
            try {
                throw IOException("Layer 1")
            } catch (t: Throwable) {
                throw IllegalStateException("Layer 2", t)
            }
        } catch (t: Throwable) {
            val desThrowable = serdes(t, factory, factory2, false)
            assertSerializedThrowableEquivalent(t, desThrowable)
        }
    }

    private fun assertSerializedThrowableEquivalent(t: Throwable, desThrowable: Throwable) {
        assertTrue(desThrowable is CordaRuntimeException) // Since we don't handle the other case(s) yet
        assertEquals("${t.javaClass.name}: ${t.message}", desThrowable.message)
        assertTrue(Objects.deepEquals(t.stackTrace.toStackTraceBasic, desThrowable.stackTrace.toStackTraceBasic))
        assertEquals(t.suppressed.size, desThrowable.suppressed.size)
        t.suppressed.zip(desThrowable.suppressed).forEach { (before, after) -> assertSerializedThrowableEquivalent(before, after) }
    }

    @Test
    fun `test suppressed throwables serialize`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(ThrowableSerializer(factory), factory)
        factory.register(StackTraceElementSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(ThrowableSerializer(factory2), factory2)
        factory2.register(StackTraceElementSerializer(), factory)

        try {
            try {
                throw IOException("Layer 1")
            } catch (t: Throwable) {
                val e = IllegalStateException("Layer 2")
                e.addSuppressed(t)
                throw e
            }
        } catch (t: Throwable) {
            val desThrowable = serdes(t, factory, factory2, false)
            assertSerializedThrowableEquivalent(t, desThrowable)
        }
    }

    @Test
    fun `test flow corda exception subclasses serialize`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(ThrowableSerializer(factory), factory)
        factory.register(StackTraceElementSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(ThrowableSerializer(factory2), factory2)
        factory2.register(StackTraceElementSerializer(), factory2)

        val obj = FlowException("message").fillInStackTrace()
        serdes(obj, factory, factory2)
    }

    @Test
    fun `test polymorphic property`() {
        val obj = PolymorphicProperty(FooImplements("Ginger", 12))
        serdes(obj)
    }

    @Test
    fun `test null polymorphic property`() {
        val obj = PolymorphicProperty(null)
        serdes(obj)
    }

    @Test
    fun `test kotlin object`() {
        serdes(KotlinObject)
    }

    @CordaSerializable
    class LedgerTransaction

    @CordaSerializable
    open class Contract {
        open fun verify(tx: LedgerTransaction) {}
    }

    @CordaSerializable
    object FooContract : Contract() {
        override fun verify(tx: LedgerTransaction) {}
    }

    @Test
    fun `test custom object`() {
        serdes(FooContract)
    }

    @Test
    fun `test month serialize`() {
        val obj = Month.APRIL
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        serdes(obj, factory, factory)
    }

    @Test
    fun `test day of week serialize`() {
        val obj = DayOfWeek.FRIDAY
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        serdes(obj, factory, factory)
    }

    @CordaSerializable
    class OtherGeneric<T : Any>

    open class GenericSuperclass<T : Any>(val param: OtherGeneric<T>)

    @CordaSerializable
    class GenericSubclass(param: OtherGeneric<String>) : GenericSuperclass<String>(param) {
        override fun equals(other: Any?): Boolean = other is GenericSubclass // This is a bit lame but we just want to
                                                                             // check it doesn't throw exceptions
        override fun hashCode(): Int = javaClass.hashCode()
    }

    @Test
    fun `test generic in constructor serialize`() {
        val obj = GenericSubclass(OtherGeneric())
        serdes(obj)
    }

    @CordaSerializable
    interface Container

    @CordaSerializable
    data class SimpleContainer(val one: String, val another: String) : Container

    @CordaSerializable
    data class ParentContainer(val left: SimpleContainer, val right: Container)

    @Test
    fun `test object referenced multiple times`() {
        val simple = SimpleContainer("Fred", "Ginger")
        val parentContainer = ParentContainer(simple, simple)
        assertSame(parentContainer.left, parentContainer.right)

        val parentCopy = serdes(parentContainer)
        assertSame(parentCopy.left, parentCopy.right)
    }

    @CordaSerializable
    data class Bob(val byteArrays: List<ByteArray>)

    @Test
    fun `test list of byte arrays`() {
        val a = ByteArray(1)
        val b = ByteArray(2)
        val obj = Bob(listOf(a, b, a))

        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val obj2 = serdes(obj, factory, factory2, expectedEqual = false, expectDeserializedEqual = false)

        assertNotSame(obj2.byteArrays[0], obj2.byteArrays[2])
    }

    @CordaSerializable
    data class Vic(val a: List<String>, val b: List<String>)

    @Test
    fun `test generics ignored from graph logic`() {
        val a = listOf("a", "b")
        val obj = Vic(a, a)

        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val objCopy = serdes(obj, factory, factory2)
        assertSame(objCopy.a, objCopy.b)
    }

    class Spike private constructor(val a: String) {
        constructor() : this("a")

        override fun equals(other: Any?): Boolean = other is Spike && other.a == this.a
        override fun hashCode(): Int = a.hashCode()
    }

    @Test
    fun `test private constructor`() {
        val obj = Spike()

        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        assertThrows<NotSerializableException> { serdes(obj, factory, factory2) }
    }

    @CordaSerializable
    data class BigDecimals(val a: BigDecimal, val b: BigDecimal)

    @Test
    fun `test toString custom serializer`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(BigDecimalSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(BigDecimalSerializer(), factory2)

        val obj = BigDecimals(BigDecimal.TEN, BigDecimal.TEN)
        val objCopy = serdes(obj, factory, factory2)
        assertEquals(objCopy.a, objCopy.b)
    }

    @CordaSerializable
    class ByteArrays(val a: ByteArray, val b: ByteArray)

    @Test
    fun `test byte arrays not reference counted`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(BigDecimalSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(BigDecimalSerializer(), factory2)

        val bytes = ByteArray(1)
        val obj = ByteArrays(bytes, bytes)
        val objCopy = serdes(obj, factory, factory2, expectedEqual = false, expectDeserializedEqual = false)
        assertNotSame(objCopy.a, objCopy.b)
    }

    @Test
    fun `test kotlin Unit serialize`() {
        val obj = Unit
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        serdes(obj, factory, factory)
    }

    @Test
    fun `test kotlin Pair serialize`() {
        val obj = Pair("a", 3)
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        serdes(obj, factory, factory)
    }

    @Test
    fun `test InputStream serialize`() {
        val factory = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory.register(InputStreamSerializer(), factory)

        val factory2 = SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup())
        factory2.register(InputStreamSerializer(), factory2)
        val bytes = ByteArray(10) { it.toByte() }
        val obj = bytes.inputStream()
        val obj2 = serdes<InputStream>(obj, factory, factory2, expectedEqual = false, expectDeserializedEqual = false)
        val obj3 = bytes.inputStream()  // Can't use original since the stream pointer has moved.
        assertEquals(obj3.available(), obj2.available())
        assertEquals(obj3.read(), obj2.read())
    }

    @Test
    fun `test EnumMap serialize`() {
        val obj = EnumMap<Month, Int>(Month::class.java)
        obj[Month.APRIL] = Month.APRIL.value
        obj[Month.AUGUST] = Month.AUGUST.value
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        serdes(obj, factory, factory)
    }

    @CordaSerializable
    data class Amount<T : Any>(val quantity: Long, val displayTokenSize: BigDecimal, val token: T)

    //
    // Example stacktrace that this test is trying to reproduce
    //
    // java.lang.IllegalArgumentException:
    //      net.corda.core.contracts.TransactionState ->
    //      data(net.corda.core.contracts.ContractState) ->
    //      net.corda.finance.contracts.asset.Cash$State ->
    //      amount(net.corda.core.contracts.Amount<net.corda.core.contracts.Issued<java.util.Currency>>) ->
    //      net.corda.core.contracts.Amount<net.corda.core.contracts.Issued<java.util.Currency>> ->
    //      displayTokenSize(java.math.BigDecimal) ->
    //      wrong number of arguments
    //
    // So the actual problem was objects with multiple getters. The code wasn't looking for one with zero
    // properties, just taking the first one it found with with the most applicable type, and the reflection
    // ordering of the methods was random, thus occasionally we select the wrong one
    //
    @Test
    fun reproduceWrongNumberOfArguments() {
        @CordaSerializable
        data class C(val a: Amount<Currency>)

        val factory = testDefaultFactoryNoEvolution()
        factory.register(BigDecimalSerializer(), factory)
        factory.register(CurrencySerializer(), factory)

        val c = C(Amount(100, BigDecimal("1.5"), Currency.getInstance("USD")))

        // were the issue not fixed we'd blow up here
        SerializationOutput(factory).serialize(c)
    }

    @Test
    fun `compression has the desired effect`() {
        val factory = testDefaultFactory()
        val data = ByteArray(12345).also { Random(0).nextBytes(it) }.let { it + it }
        val compressed = SerializationOutput(factory).serialize(data, CordaSerializationEncoding.SNAPPY)
        assertEquals(.5, compressed.size.toDouble() / data.size, .03)

        val encodingWhitelist = mock(EncodingWhitelist::class.java)
        doReturn(true).whenever(encodingWhitelist).acceptEncoding(CordaSerializationEncoding.SNAPPY)
        assertArrayEquals(data, DeserializationInput(factory).deserialize(
            compressed, testSerializationContext.withEncodingWhitelist(encodingWhitelist)
        ))
    }

    @Test
    fun `a particular encoding can be banned for deserialization`() {
        val factory = testDefaultFactory()
        val encodingWhitelist = mock(EncodingWhitelist::class.java)
        doReturn(false).whenever(encodingWhitelist).acceptEncoding(CordaSerializationEncoding.SNAPPY)
        val compressed = SerializationOutput(factory).serialize("whatever", CordaSerializationEncoding.SNAPPY)
        val input = DeserializationInput(factory)
        catchThrowable { input.deserialize(compressed, testSerializationContext.withEncodingWhitelist(encodingWhitelist)) }.run {
            assertSame(NotSerializableException::class.java, javaClass)
            assertEquals(encodingNotPermittedFormat.format(CordaSerializationEncoding.SNAPPY), message)
        }
    }

    @Test
    fun nestedObjects() {
        // The "test" is that this doesn't throw, anything else is a success
        AckWrapper.serialize()
    }

    @Test
    fun privateNestedObjects() {
        // The "test" is that this doesn't throw, anything else is a success
        PrivateAckWrapper.serialize()
    }

    @Test
    fun throwable() {
        class TestException(message: String?, cause: Throwable?) : CordaRuntimeException(message, cause)

        val testExcp = TestException("hello", Throwable().apply { stackTrace = Thread.currentThread().stackTrace })
        val factory = testDefaultFactoryNoEvolution().also { registerCustomSerializers(it) }
        SerializationOutput(factory).serialize(testExcp)
    }

    @Test
    fun nestedInner() {
        class C(val a: Int) {
            inner class D(val b: Int)

            fun serialize() {
                val factory = testDefaultFactoryNoEvolution()
                SerializationOutput(factory).serialize(D(4))
            }
        }

        // By the time we escape the serializer we should just have a general
        // NotSerializable Exception
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            C(12).serialize()
        }.withMessageContaining("has synthetic fields and is likely a nested inner class")
    }

    @Test
    fun nestedNestedInner() {
        class C(val a: Int) {
            inner class D(val b: Int) {
                inner class E(val c: Int)

                fun serialize() {
                    val factory = testDefaultFactoryNoEvolution()
                    SerializationOutput(factory).serialize(E(4))
                }
            }

            fun serializeD() {
                val factory = testDefaultFactoryNoEvolution()
                SerializationOutput(factory).serialize(D(4))
            }

            fun serializeE() {
                D(1).serialize()
            }
        }

        // By the time we escape the serializer we should just have a general
        // NotSerializable Exception
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            C(12).serializeD()
        }.withMessageContaining("has synthetic fields and is likely a nested inner class")

        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            C(12).serializeE()
        }.withMessageContaining("has synthetic fields and is likely a nested inner class")
    }

    @Test
    fun multiNestedInner() {
        class C(val a: Int) {
            inner class D(val b: Int)
            inner class E(val c: Int)

            fun serializeD() {
                val factory = testDefaultFactoryNoEvolution()
                SerializationOutput(factory).serialize(D(4))
            }

            fun serializeE() {
                val factory = testDefaultFactoryNoEvolution()
                SerializationOutput(factory).serialize(E(4))
            }
        }

        // By the time we escape the serializer we should just have a general
        // NotSerializable Exception
        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            C(12).serializeD()
        }.withMessageContaining("has synthetic fields and is likely a nested inner class")

        assertThatExceptionOfType(NotSerializableException::class.java).isThrownBy {
            C(12).serializeE()
        }.withMessageContaining("has synthetic fields and is likely a nested inner class")
    }

    @CordaSerializable
    interface DataClassByInterface<V> {
        val v: V
    }

    @Test
    @SuppressWarnings("TooGenericExceptionThrown")
    fun dataClassBy() {
        data class C(val s: String) : DataClassByInterface<String> {
            override val v: String = "-- $s"
        }

        @CordaSerializable
        data class Inner<T>(val wrapped: DataClassByInterface<T>) : DataClassByInterface<T> by wrapped {
            override val v = wrapped.v
        }

        val i = Inner(C("hello"))

        val bytes = SerializationOutput(testDefaultFactory()).serialize(i)

        try {
            DeserializationInput(testDefaultFactory()).deserialize(bytes, testSerializationContext)
        } catch (e: NotSerializableException) {
            throw Error("Deserializing serialized \$C should not throw")
        }
    }

    @Test
    fun `compression reduces number of bytes significantly`() {
        val ser = SerializationOutput(SerializerFactoryBuilder.build(testSerializationContext.currentSandboxGroup()))
        val obj = ByteArray(20000)
        val uncompressedSize = ser.serialize(obj).bytes.size
        val compressedSize = ser.serialize(obj, CordaSerializationEncoding.SNAPPY).bytes.size
        // Ordinarily this might be considered high maintenance, but we promised wire compatibility, so they'd better not change!
        // Different than Corda 4, because this includes the Metadata element.
        assertEquals(20097, uncompressedSize)
        assertEquals(1044, compressedSize)
    }

    // JDK11: backwards compatibility function to deal with StacktraceElement comparison pre-JPMS
    private fun deepEquals(a: Any?, b: Any?): Boolean {
        return if (a === b)
            true
        else if (a == null || b == null)
            false
        else {
            if (a is Exception && b is Exception)
                (a.cause == b.cause && a.localizedMessage == b.localizedMessage && a.message == b.message) &&
                        Objects.deepEquals(a.stackTrace.toStackTraceBasic, b.stackTrace.toStackTraceBasic)
            else
                Objects.deepEquals(a, b)
        }
    }

    private val <T> Array<T>.toStackTraceBasic: Unit
        get() {
            this.map { StackTraceElementBasic(it as StackTraceElement) }
        }

    // JPMS adds additional fields that are not equal according to classloader/module hierarchy
    data class StackTraceElementBasic(val ste: StackTraceElement) {
        override fun equals(other: Any?): Boolean {
            return if (other is StackTraceElementBasic)
                (ste.className == other.ste.className) &&
                        (ste.methodName == other.ste.methodName) &&
                        (ste.fileName == other.ste.fileName) &&
                        (ste.lineNumber == other.ste.lineNumber)
            else false
        }
    }
}