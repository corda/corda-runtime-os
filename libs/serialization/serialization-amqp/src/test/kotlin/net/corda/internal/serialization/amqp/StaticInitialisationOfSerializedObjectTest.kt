package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.serialization.ClassWhitelist
import net.corda.v5.serialization.SerializedBytes
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class InStatic : Exception("Help!, help!, I'm being repressed")

class C {
    companion object {
        init {
            throw InStatic()
        }
    }
}

// To re-setup the resource file for the tests
//   * deserializeTest
//   * deserializeTest2
// comment out the companion object from here,  comment out the test code and uncomment
// the generation code, then re-run the test and copy the file shown in the output print
// to the resource directory
class C2(var b: Int) {
    companion object {
        init {
            throw InStatic()
        }
    }
}

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class StaticInitialisationOfSerializedObjectTest {
    @Test
    fun itBlowsUp() {
        assertThrows<ExceptionInInitializerError> { C() }
    }

    @Disabled("Suppressing this, as it depends on obtaining internal access to serialiser cache")
    @Test
	fun kotlinObjectWithCompanionObject() {
        data class D(val c: C)

        val sf = SerializerFactoryBuilder.build(AllWhitelist, testSerializationContext.currentSandboxGroup())

        val typeMap = sf::class.java.getDeclaredField("serializersByType")
        typeMap.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val serialisersByType = typeMap.get(sf) as ConcurrentHashMap<Type, AMQPSerializer<Any>>

        // pre building a serializer, we shouldn't have anything registered
        assertEquals(0, serialisersByType.size)

        // build a serializer for type D without an instance of it to serialise, since
        // we can't actually construct one
        sf.get(D::class.java)

        // post creation of the serializer we should have two elements in the map, this
        // proves we didn't statically construct an instance of C when building the serializer
        assertEquals(2, serialisersByType.size)
    }

    @Test
	fun deserializeTest() {
        data class D(val c: C2)

        val resource = "StaticInitialisationOfSerializedObjectTest.deserializeTest"
        val url = EvolvabilityTests::class.java.getResource(resource)!!

        // Original version of the class for the serialised version of this class
        //
//        val sf1 = SerializerFactoryBuilder.build(AllWhitelist, ClassLoader.getSystemClassLoader())
//        val sc = SerializationOutput(sf1).serialize(D(C2(20)))
//        File(URI("$localPath/$resource")).writeBytes(sc.bytes)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) =
                    type.name == "net.corda.v5.serialization.internal.amqp" +
                            ".StaticInitialisationOfSerializedObjectTest\$deserializeTest\$D"
        }

        val whitelist = WL()
        val sf2 = SerializerFactoryBuilder.build(whitelist, testSerializationContext.currentSandboxGroup())
        val bytes = url.readBytes()

        assertThatThrownBy {
            DeserializationInput(sf2).deserialize(SerializedBytes<D>(bytes))
        }.isInstanceOf(NotSerializableException::class.java)
    }
}