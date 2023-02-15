package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.custom.InstantSerializer
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.amqp.testutils.ProjectStructure.projectRootDir
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testName
import net.corda.v5.base.annotations.ConstructorForDeserialization
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DeprecatedConstructorForDeserialization
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.NotSerializableException
import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

// To regenerate any of the binary test files do the following
//
//  0. set localPath accordingly
//  1. Uncomment the code where the original form of the class is defined in the test
//  2. Comment out the rest of the test
//  3. Run the test
//  4. Using the printed path copy that file to the resources directory
//  5. Comment back out the generation code and uncomment the actual test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
@Suppress("VariableNaming")
class EvolvabilityTests {
    // When regenerating the test files this needs to be set to the file system location of the resource files
    @Suppress("UNUSED")
    var localPath: URI = projectRootDir.toUri().resolve(
        "libs/serialization/serialization-amqp/src/test/resources/net/corda/internal/serialization/amqp"
    )

    @Test
    fun simpleOrderSwapSameType() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.simpleOrderSwapSameType"

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        // @CordaSerializable
        // data class C (val a: Int, val b: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        @CordaSerializable
        data class C(val b: Int, val a: Int)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(bytes = SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun simpleOrderSwapSameTypeWithDefaultConstructorParam() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.simpleOrderSwapSameTypeWithDefaultConstructorParam"

        val A = 1
        val B = 2

        // Original version of the class for the serialised version of this class
        // @CordaSerializable
        // data class C (val a: Int, val b: Int, val c: Int = 5)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        @CordaSerializable
        data class C(val b: Int, val a: Int, val c: Int = 5)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(bytes = SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun simpleOrderSwapDifferentType() {
        val sf = testDefaultFactory()
        val A = 1
        val B = "two"
        val resource = "EvolvabilityTests.simpleOrderSwapDifferentType"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class C (val a: Int, val b: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A, B)).bytes)

        // new version of the class, in this case the order of the parameters has been swapped
        @CordaSerializable
        data class C(val b: String, val a: Int)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(B, deserializedC.b)
    }

    @Test
    fun addAdditionalParamNotMandatory() {
        val sf = testDefaultFactory()
        val A = 1
        val resource = "EvolvabilityTests.addAdditionalParamNotMandatory"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class C(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(A)).bytes)

        @CordaSerializable
        data class C(val a: Int, val b: Int?)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(A, deserializedC.a)
        assertEquals(null, deserializedC.b)
    }

    @Test
    fun addAdditionalParam() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addAdditionalParam"
        val url = EvolvabilityTests::class.java.getResource(resource)
        @Suppress("UNUSED_VARIABLE")
        val A = 1

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class C(val a: Int)
        // val sc = SerializationOutput(sf).serialize(C(A))
        // File(URI("$localPath/$resource")).writeBytes(sc.bytes)

        // new version of the class, in this case a new parameter has been added (b)
        data class C(val a: Int, val b: Int)

        val sc2 = url.readBytes()

        // Expected to throw as we can't construct the new type as it contains a newly
        // added parameter that isn't optional, i.e. not nullable and there isn't
        // a constructor that takes the old parameters
        assertThrows<NotSerializableException> {
            DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))
        }
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun removeParameters() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.removeParameters"
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C, D)).bytes)

        @CordaSerializable
        data class CC(val b: String, val d: Int)

        val url = EvolvabilityTests::class.java.getResource("EvolvabilityTests.removeParameters")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(B, deserializedCC.b)
        assertEquals(D, deserializedCC.d)
    }

    @Suppress("UNUSED_VARIABLE")
    @Test
    fun addAndRemoveParameters() {
        val sf = testDefaultFactory()
        val A = 1
        val B = "two"
        val C = "three"
        val D = 4
        val E = null

        val resource = "EvolvabilityTests.addAndRemoveParameters"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int, val b: String, val c: String, val d: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C, D)).bytes)

        @CordaSerializable
        data class CC(val a: Int, val e: Boolean?, val d: Int)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(E, deserializedCC.e)
        assertEquals(D, deserializedCC.d)
    }

    @Test
    fun addMandatoryFieldWithAltConstructor() {
        val sf = testDefaultFactory()
        val A = 1
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructor"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A)).bytes)

        @Suppress("UNUSED")
        @CordaSerializable
        data class CC(val a: Int, val b: String) {
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (a: Int) : this(a, "hello")
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals("hello", deserializedCC.b)
    }

    @Test
    fun addMandatoryFieldWithAltConstructorForceReorder() {
        val sf = testDefaultFactory()
        val z = 30
        val y = 20
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorForceReorder"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val z: Int, val y: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(z, y)).bytes)

        @Suppress("UNUSED")
        @CordaSerializable
        data class CC(val z: Int, val y: Int, val a: String) {
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (z: Int, y: Int) : this(z, y, "10")
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(url.readBytes()))

        assertEquals("10", deserializedCC.a)
        assertEquals(y, deserializedCC.y)
        assertEquals(z, deserializedCC.z)
    }

    @Test
    fun moreComplexNonNullWithReorder() {
        val resource = "${javaClass.simpleName}.${testName()}"

        @CordaSerializable
        data class NetworkParametersExample(
            val minimumPlatformVersion: Int,
            val notaries: List<String>,
            val maxMessageSize: Int,
            val maxTransactionSize: Int,
            val modifiedTime: Instant,
            val epoch: Int,
            val onAllowListContractImplementations: Map<String, List<Int>>
        )

        val factory = testDefaultFactory().apply {
            register(InstantSerializer(), this)
        }

        // Uncomment to regenerate test case
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(factory).serialize(
        //         NetworkParametersExample(
        //                 10,
        //                 listOf("Notary1", "Notary2"),
        //                 100,
        //                 10,
        //                 Instant.now(),
        //                 9,
        //                 mapOf("A" to listOf(1, 2, 3), "B" to listOf (4, 5, 6)))).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)
        assertNotNull(url)
        DeserializationInput(factory).deserialize(SerializedBytes<NetworkParametersExample>(url.readBytes()))
    }

    @Test
    @Suppress("UNUSED")
    fun addMandatoryFieldWithAltConstructorUnAnnotated() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorUnAnnotated"
        val url = EvolvabilityTests::class.java.getResource(resource)
        @Suppress("UNUSED_VARIABLE")
        val A = 1

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int)
        // val scc = SerializationOutput(sf).serialize(CC(A))
        // File(URI("$localPath/$resource")).writeBytes(scc.bytes)

        data class CC(val a: Int, val b: String) {
            // constructor annotation purposefully omitted
            constructor (a: Int) : this(a, "hello")
        }

        // we expect this to throw as we should not find any constructors
        // capable of dealing with this
        assertThrows<NotSerializableException> {
            DeserializationInput(sf).deserialize(SerializedBytes<CC>(url.readBytes()))
        }
    }

    @Test
    fun addMandatoryFieldWithAltReorderedConstructor() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltReorderedConstructor"
        val A = 1
        val B = 100
        val C = "This is not a banana"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int, val b: Int, val c: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C)).bytes)

        @Suppress("UNUSED")
        @CordaSerializable
        data class CC(val a: Int, val b: Int, val c: String, val d: String) {
            // ensure none of the original parameters align with the initial
            // construction order
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (c: String, a: Int, b: Int) : this(a, b, c, "wibble")
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(B, deserializedCC.b)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test
    fun addMandatoryFieldWithAltReorderedConstructorAndRemoval() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltReorderedConstructorAndRemoval"
        val A = 1
        @Suppress("UNUSED_VARIABLE")
        val B = 100
        val C = "This is not a banana"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int, val b: Int, val c: String)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(A, B, C)).bytes)

        // b is removed, d is added
        @CordaSerializable
        data class CC(val a: Int, val c: String, val d: String) {
            // ensure none of the original parameters align with the initial
            // construction order
            @Suppress("UNUSED")
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (c: String, a: Int) : this(a, c, "wibble")
        }

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(A, deserializedCC.a)
        assertEquals(C, deserializedCC.c)
        assertEquals("wibble", deserializedCC.d)
    }

    @Test
    fun multiVersion() {
        val sf = testDefaultFactory()
        val resource1 = "EvolvabilityTests.multiVersion.1"
        val resource2 = "EvolvabilityTests.multiVersion.2"
        val resource3 = "EvolvabilityTests.multiVersion.3"

        val a = 100
        val b = 200
        val c = 300
        val d = 400

        // Original version of the class as it was serialised
        //
        // Version 1:
        // @CordaSerializable
        // data class C (val a: Int, val b: Int)
        // File(URI("$localPath/$resource1")).writeBytes(SerializationOutput(sf).serialize(C(a, b)).bytes)
        //
        // Version 2 - add param c
        // @CordaSerializable
        // data class C (val c: Int, val b: Int, val a: Int)
        // File(URI("$localPath/$resource2")).writeBytes(SerializationOutput(sf).serialize(C(c, b, a)).bytes)
        //
        // Version 3 - add param d
        // @CordaSerializable
        // data class C (val b: Int, val c: Int, val d: Int, val a: Int)
        // File(URI("$localPath/$resource3")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, a)).bytes)

        @Suppress("UNUSED")
        @CordaSerializable
        data class C(val e: Int, val c: Int, val b: Int, val a: Int, val d: Int) {
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (b: Int, a: Int) : this(-1, -1, b, a, -1)

            @DeprecatedConstructorForDeserialization(version = 2)
            constructor (a: Int, c: Int, b: Int) : this(-1, c, b, a, -1)

            @DeprecatedConstructorForDeserialization(version = 3)
            constructor (a: Int, b: Int, c: Int, d: Int) : this(-1, c, b, a, d)
        }

        val url1 = EvolvabilityTests::class.java.getResource(resource1)
        val url2 = EvolvabilityTests::class.java.getResource(resource2)
        val url3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = url1.readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(a, db1.a)
        assertEquals(b, db1.b)
        assertEquals(-1, db1.c)
        assertEquals(-1, db1.d)
        assertEquals(-1, db1.e)

        val sb2 = url2.readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(a, db2.a)
        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(-1, db2.d)
        assertEquals(-1, db2.e)

        val sb3 = url3.readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(a, db3.a)
        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(-1, db3.e)
    }

    @Test
    fun changeSubType() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.changeSubType"
        val oa = 100
        val ia = 200

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class Inner (val a: Int)
        // @CordaSerializable
        // data class Outer (val a: Int, val b: Inner)
        // File(URI("$localPath/$resource"))
        //     .writeBytes(SerializationOutput(sf)
        //         .serialize(Outer(oa, Inner (ia))).bytes)

        // Add a parameter to inner but keep outer unchanged
        @CordaSerializable
        data class Inner(val a: Int, val b: String?)

        @CordaSerializable
        data class Outer(val a: Int, val b: Inner)

        val url = EvolvabilityTests::class.java.getResource(resource)
        val sc2 = url.readBytes()
        val outer = DeserializationInput(sf).deserialize(SerializedBytes<Outer>(sc2))

        assertEquals(oa, outer.a)
        assertEquals(ia, outer.b.a)
        assertEquals(null, outer.b.b)

        // Repeat, but receiving a message with the newer version of Inner
        val newVersion = SerializationOutput(sf).serializeAndReturnSchema(Outer(oa, Inner(ia, "new value")))
        val model = AMQPRemoteTypeModel()
        val remoteTypeInfo = model.interpret(
            SerializationSchemas(newVersion.schema, newVersion.transformsSchema),
            testSerializationContext.currentSandboxGroup()
        )
        println(remoteTypeInfo)

        val newOuter = DeserializationInput(sf).deserialize(SerializedBytes<Outer>(newVersion.obj.bytes))
        assertEquals(oa, newOuter.a)
        assertEquals(ia, newOuter.b.a)
        assertEquals("new value", newOuter.b.b)
    }

    @Test
    fun multiVersionWithRemoval() {
        val sf = testDefaultFactory()

        val resource1 = "EvolvabilityTests.multiVersionWithRemoval.1"
        val resource2 = "EvolvabilityTests.multiVersionWithRemoval.2"
        val resource3 = "EvolvabilityTests.multiVersionWithRemoval.3"

        @Suppress("UNUSED_VARIABLE")
        val a = 100
        val b = 200
        val c = 300
        val d = 400
        val e = 500
        val f = 600

        // Original version of the class as it was serialised
        //
        // Version 1:
        // @CordaSerializable
        // data class C (val a: Int, val b: Int, val c: Int)
        // File(URI("$localPath/$resource1")).writeBytes(SerializationOutput(sf).serialize(C(a, b, c)).bytes)
        //
        // Version 2 - remove property a, add property e
        // @CordaSerializable
        // data class C (val b: Int, val c: Int, val d: Int, val e: Int)
        // File(URI("$localPath/$resource2")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, e)).bytes)
        //
        // Version 3 - add param d
        // @CordaSerializable
        // data class C (val b: Int, val c: Int, val d: Int, val e: Int, val f: Int)
        // File(URI("$localPath/$resource3")).writeBytes(SerializationOutput(sf).serialize(C(b, c, d, e, f)).bytes)

        @Suppress("UNUSED")
        @CordaSerializable
        data class C(val b: Int, val c: Int, val d: Int, val e: Int, val f: Int, val g: Int) {
            @DeprecatedConstructorForDeserialization(version = 1)
            constructor (b: Int, c: Int) : this(b, c, -1, -1, -1, -1)

            @DeprecatedConstructorForDeserialization(version = 2)
            constructor (b: Int, c: Int, d: Int) : this(b, c, d, -1, -1, -1)

            @DeprecatedConstructorForDeserialization(version = 3)
            constructor (b: Int, c: Int, d: Int, e: Int) : this(b, c, d, e, -1, -1)

            @DeprecatedConstructorForDeserialization(version = 4)
            constructor (b: Int, c: Int, d: Int, e: Int, f: Int) : this(b, c, d, e, f, -1)
        }

        val url1 = EvolvabilityTests::class.java.getResource(resource1)
        val url2 = EvolvabilityTests::class.java.getResource(resource2)
        val url3 = EvolvabilityTests::class.java.getResource(resource3)

        val sb1 = url1.readBytes()
        val db1 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb1))

        assertEquals(b, db1.b)
        assertEquals(c, db1.c)
        assertEquals(-1, db1.d) // must not be set by calling constructor 2 by mistake
        assertEquals(-1, db1.e)
        assertEquals(-1, db1.f)
        assertEquals(-1, db1.g)

        val sb2 = url2.readBytes()
        val db2 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb2))

        assertEquals(b, db2.b)
        assertEquals(c, db2.c)
        assertEquals(d, db2.d)
        assertEquals(e, db2.e)
        assertEquals(-1, db2.f)
        assertEquals(-1, db1.g)

        val sb3 = url3.readBytes()
        val db3 = DeserializationInput(sf).deserialize(SerializedBytes<C>(sb3))

        assertEquals(b, db3.b)
        assertEquals(c, db3.c)
        assertEquals(d, db3.d)
        assertEquals(e, db3.e)
        assertEquals(f, db3.f)
        assertEquals(-1, db3.g)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun getterSetterEvolver1() {
        val resource = "EvolvabilityTests.getterSetterEvolver1"
        val sf = testDefaultFactory()

        //
        // Class as it was serialised
        //
        // @CordaSerializable
        // data class C(var c: Int, var d: Int, var b: Int, var e: Int, var a: Int) {
        //     // This will force the serialization engine to use getter / setter
        //     // instantiation for the object rather than construction
        //     @ConstructorForDeserialization
        //     @Suppress("UNUSED")
        //     constructor() : this(0, 0, 0, 0, 0)
        // }
        //
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(C(3,4,2,5,1)).bytes)

        //
        // Class as it exists now, c has been removed
        //
        @CordaSerializable
        data class C(var d: Int, var b: Int, var e: Int, var a: Int) {
            // This will force the serialization engine to use getter / setter
            // instantiation for the object rather than construction
            @ConstructorForDeserialization
            @Suppress("UNUSED")
            constructor() : this(0, 0, 0, 0)
        }

        val url = EvolvabilityTests::class.java.getResource(resource)

        val sc2 = url.readBytes()
        val deserializedC = DeserializationInput(sf).deserialize(SerializedBytes<C>(sc2))

        assertEquals(1, deserializedC.a)
        assertEquals(2, deserializedC.b)
        assertEquals(4, deserializedC.d)
        assertEquals(5, deserializedC.e)
    }

    // Container class
    @CordaSerializable
    data class ParameterizedContainer(val parameterized: Parameterized<Int, Int>?)
    // Class as it was serialized
    // data class Parameterized<A, B>(val a: A, val b: Set<B>)

    // Marker interface to force evolution
    interface ForceEvolution

    // Class after evolution
    @CordaSerializable
    data class Parameterized<A, B>(val a: A, val b: Set<B>) : ForceEvolution

    // See CORDA-2742
    @Test
    fun evolutionWithPrimitives() {
        val resource = "EvolvabilityTests.evolutionWithPrimitives"
        val sf = testDefaultFactory()
        // Uncomment to recreate
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf)
        //     .serialize(ParameterizedContainer(Parameterized(10, setOf(20)))).bytes)

        val url = EvolvabilityTests::class.java.getResource(resource)

        val sc2 = url.readBytes()
        val deserialized = DeserializationInput(sf).deserialize(SerializedBytes<ParameterizedContainer>(sc2))

        assertEquals(10, deserialized.parameterized?.a)
    }

    @Test
    fun addMandatoryFieldWithAltConstructorAndMakeExistingIntFieldNullable() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorAndMakeExistingIntFieldNullable"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(1)).bytes)

        @CordaSerializable
        data class CC(val a: Int?, val b: Int) {
            @DeprecatedConstructorForDeserialization(version = 1)
            @Suppress("unused")
            constructor(a: Int) : this(a, 42)
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(1, deserializedCC.a)
        assertEquals(42, deserializedCC.b)
    }

    @Test
    fun addMandatoryFieldWithAltConstructorAndMakeExistingNullableIntFieldMandatory() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldWithAltConstructorAndMakeExistingNullableIntFieldMandatory"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val a: Int?)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC(null)).bytes)

        @CordaSerializable
        data class CC(val a: Int, val b: Int) {
            @DeprecatedConstructorForDeserialization(version = 1)
            @Suppress("unused")
            constructor(a: Int?) : this(a ?: -1, 42)
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals(-1, deserializedCC.a)
        assertEquals(42, deserializedCC.b)
    }

    @Test
    fun addMandatoryFieldAndRemoveExistingNullableIntField() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.addMandatoryFieldAndRemoveExistingNullableIntField"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val data: String, val a: Int?)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC("written", null)).bytes)

        @CordaSerializable
        data class CC(val data: String, val b: String) {
            @DeprecatedConstructorForDeserialization(version = 1)
            @Suppress("unused")
            constructor(data: String, a: Int?) : this(data, a?.toString() ?: "<not provided>")
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals("written", deserializedCC.data)
        assertEquals("<not provided>", deserializedCC.b)
    }

    @Test
    fun removeExistingNullableIntFieldWithAltConstructor() {
        val sf = testDefaultFactory()
        val resource = "EvolvabilityTests.removeExistingNullableIntFieldWithAltConstructor"

        // Original version of the class as it was serialised
        // @CordaSerializable
        // data class CC(val data: String, val a: Int?)
        // File(URI("$localPath/$resource")).writeBytes(SerializationOutput(sf).serialize(CC("written", null)).bytes)

        @CordaSerializable
        data class CC(val data: String) {
            @DeprecatedConstructorForDeserialization(version = 1)
            @Suppress("unused")
            constructor(data: String, a: Int?) : this(data + (a?.toString() ?: "<not provided>"))
        }

        val url = EvolvabilityTests::class.java.getResource(resource) ?: fail("Not found!")
        val sc2 = url.readBytes()
        val deserializedCC = DeserializationInput(sf).deserialize(SerializedBytes<CC>(sc2))

        assertEquals("written<not provided>", deserializedCC.data)
    }
}
