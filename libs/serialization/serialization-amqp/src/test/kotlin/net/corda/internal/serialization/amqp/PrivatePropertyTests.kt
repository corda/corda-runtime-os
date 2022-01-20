package net.corda.internal.serialization.amqp

import net.corda.v5.serialization.annotations.ConstructorForDeserialization
import net.corda.internal.serialization.amqp.testutils.TestDescriptorBasedSerializerRegistry
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.model.ConfigurableLocalTypeModel
import net.corda.internal.serialization.model.LocalPropertyInformation
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.v5.base.annotations.CordaSerializable
import org.assertj.core.api.Assertions
import org.assertj.core.api.Java6Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.NotSerializableException
import java.util.Objects
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PrivatePropertyTests {

    private val registry = TestDescriptorBasedSerializerRegistry()
    private val factory = testDefaultFactoryNoEvolution(registry)
    val typeModel = ConfigurableLocalTypeModel(LocalTypeModelConfigurationImpl(factory))

    @Test
	fun testWithOnePrivateProperty() {
        @CordaSerializable
        data class C(private val b: String)

        val c1 = C("Pants are comfortable sometimes")
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertThat(c1).isEqualTo(c2)
    }

    @Test
	fun testWithOnePrivatePropertyBoolean() {
        @CordaSerializable
        data class C(private val b: Boolean)

        C(false).apply {
            assertThat(this).isEqualTo(DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(this)))
        }
    }

    @Test
	fun testWithOnePrivatePropertyNullableNotNull() {
        @CordaSerializable
        data class C(private val b: String?)

        val c1 = C("Pants are comfortable sometimes")
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertThat(c1).isEqualTo(c2)
    }

    @Test
	fun testWithOnePrivatePropertyNullableNull() {
        @CordaSerializable
        data class C(private val b: String?)

        val c1 = C(null)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertThat(c1).isEqualTo(c2)
    }

    @Test
	fun testWithOnePublicOnePrivateProperty() {
        @CordaSerializable
        data class C(val a: Int, private val b: Int)

        val c1 = C(1, 2)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertThat(c1).isEqualTo(c2)
    }

    @Test
	fun testWithInheritance() {
        open class B(val a: String, protected val b: String)
        @CordaSerializable
        class D (a: String, b: String) : B (a, b) {
            override fun equals(other: Any?): Boolean = when (other) {
                is D -> other.a == a && other.b == b
                else -> false
            }
            override fun hashCode(): Int = Objects.hash(a, b)
        }

        val d1 = D("clump", "lump")
        val d2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(d1))
        assertThat(d1).isEqualTo(d2)
    }

    @Test
	fun testMultiArgSetter() {
        @Suppress("UNUSED")
        @CordaSerializable
        data class C(private var a: Int, var b: Int) {
            // This will force the serialization engine to use getter / setter
            // instantiation for the object rather than construction
            @ConstructorForDeserialization
            constructor() : this(0, 0)

            fun setA(a: Int, @Suppress("UNUSED_PARAMETER") b: Int) { this.a = a }
            fun getA() = a
        }

        val c1 = C(33, 44)
        val c2 = DeserializationInput(factory).deserialize(SerializationOutput(factory).serialize(c1))
        assertThat(c2.getA()).isEqualTo(0)
        assertThat(c2.b).isEqualTo(44)
    }

    @Test
	fun testBadTypeArgSetter() {
        @Suppress("UNUSED")
        data class C(private var a: Int, val b: Int) {
            @ConstructorForDeserialization
            constructor() : this(0, 0)

            fun setA(a: String) { this.a = a.toInt() }
            fun getA() = a
        }

        val c1 = C(33, 44)
        Assertions.assertThatThrownBy {
            SerializationOutput(factory).serialize(c1)
        }.isInstanceOf(NotSerializableException::class.java).hasMessageContaining(
                "Defined setter for parameter a takes parameter of type class java.lang.String " +
                        "yet underlying type is int")
    }

    @Test
	fun testWithOnePublicOnePrivateProperty2() {
        @CordaSerializable
        data class C(val a: Int, private val b: Int)

        val c1 = C(1, 2)
        val schemaAndBlob = SerializationOutput(factory).serializeAndReturnSchema(c1)
        assertThat(schemaAndBlob.schema.types.size).isEqualTo(1)

        val typeInformation = typeModel.inspect(C::class.java)
        assertThat(typeInformation is LocalTypeInformation.Composable).isTrue()
        typeInformation as LocalTypeInformation.Composable

        assertThat(typeInformation.properties.size).isEqualTo(2)
        assertThat(typeInformation.properties["a"] is LocalPropertyInformation.ConstructorPairedProperty).isTrue()
        assertThat(typeInformation.properties["b"] is LocalPropertyInformation.PrivateConstructorPairedProperty).isTrue()
    }

    @Test
	fun testGetterMakesAPublicReader() {
        @CordaSerializable
        data class C(val a: Int, private val b: Int) {
            @Suppress("UNUSED")
            fun getB() = b
        }

        val c1 = C(1, 2)
        val schemaAndBlob = SerializationOutput(factory).serializeAndReturnSchema(c1)
        assertThat(schemaAndBlob.schema.types.size).isEqualTo(1)

        val typeInformation = typeModel.inspect(C::class.java)
        assertThat(typeInformation is LocalTypeInformation.Composable).isTrue()
        typeInformation as LocalTypeInformation.Composable

        assertThat(typeInformation.properties.size).isEqualTo(2)
        assertThat(typeInformation.properties["a"] is LocalPropertyInformation.ConstructorPairedProperty).isTrue()
        assertThat(typeInformation.properties["b"] is LocalPropertyInformation.ConstructorPairedProperty).isTrue()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
	fun testNested() {
        @CordaSerializable
        data class Inner(private val a: Int)
        @CordaSerializable
        data class Outer(private val i: Inner)

        val c1 = Outer(Inner(1010101))
        val output = SerializationOutput(factory).serializeAndReturnSchema(c1)

        val serializersByDescriptor = registry.contents

        // Inner and Outer
        assertThat(serializersByDescriptor.size).isEqualTo(2)

        val c2 = DeserializationInput(factory).deserialize(output.obj)
        assertThat(c1).isEqualTo(c2)
    }

    //
    // Reproduces CORDA-1134
    //
    @Suppress("UNCHECKED_CAST")
    @Test
	fun allCapsProprtyNotPrivate() {
        data class C (val CCC: String)
        val typeInformation = typeModel.inspect(C::class.java)

        assertThat(typeInformation is LocalTypeInformation.Composable).isTrue()
        typeInformation as LocalTypeInformation.Composable

        assertThat(typeInformation.properties.size).isEqualTo(1)
        assertThat(typeInformation.properties["CCC"] is LocalPropertyInformation.ConstructorPairedProperty).isTrue()
    }
}