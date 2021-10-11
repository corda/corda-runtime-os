package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.accessAsClass
import net.corda.internal.serialization.amqp.testutils.serializeAndReturnSchema
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.model.RemoteTypeInformation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID

@Timeout(value = 30)
class AMQPRemoteTypeModelTests {

    private val factory = testDefaultFactoryNoEvolution()
    private val typeModel = AMQPRemoteTypeModel()

    interface Interface<P, Q, R> {
        val array: Array<out P>
        val list: List<Q>
        val map: Map<Q, R>
    }

    enum class Enum : Interface<String, IntArray, Int> {
        FOO, BAR, BAZ;

        override val array: Array<out String> get() = emptyArray()
        override val list: List<IntArray> get() = emptyList()
        override val map: Map<IntArray, Int> get() = emptyMap()
    }

    open class Superclass<K, V>(override val array: Array<out String>, override val list: List<K>, override val map: Map<K, V>)
        : Interface<String, K, V>

    class C<V>(array: Array<out String>, list: List<UUID>, map: Map<UUID, V>, val enum: Enum): Superclass<UUID, V>(array, list, map)

    class SimpleClass(val a: Int, val b: Double, val c: Short?, val d: ByteArray, val e: ByteArray?)

    @Test
    fun `round-trip some types through AMQP serialisations`() {
        arrayOf("").assertRemoteType("String[]")
        listOf(1).assertRemoteType("List<Object>")
        arrayOf(listOf(1)).assertRemoteType("List[]")
        Enum.BAZ.assertRemoteType("Enum(FOO|BAR|BAZ)")
        mapOf("string" to 1).assertRemoteType("Map<Object, Object>")
        arrayOf(byteArrayOf(1, 2, 3)).assertRemoteType("byte[][]")

        SimpleClass(1, 2.0, null, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6))
            .assertRemoteType("""
                SimpleClass
                  a: int
                  b: double
                  c (optional): Short
                  d: byte[]
                  e (optional): byte[]
                """)

        C(arrayOf("a", "b"), listOf(UUID.randomUUID()), mapOf(UUID.randomUUID() to intArrayOf(1, 2, 3)), Enum.BAZ)
            .assertRemoteType("""
            C: Interface<String, UUID, Object>
              array: String[]
              enum: Enum(FOO|BAR|BAZ)
              list: List<UUID>
              map: Map<UUID, Object>
        """)
    }

    private fun getRemoteType(obj: Any): RemoteTypeInformation {
        val schema = SerializationOutput(factory).serializeAndReturnSchema(obj)
        schema.schema.types.forEach { println(it) }
        val values = typeModel.interpret(SerializationSchemas(schema.schema, schema.transformsSchema)).values
        return values.find { it.typeIdentifier.getLocalType().accessAsClass().isAssignableFrom(obj::class.java) } ?:
        throw IllegalArgumentException(
            "Can't find ${obj::class.java.name} in ${values.map { it.typeIdentifier.name}}")
    }

    private fun Any.assertRemoteType(prettyPrinted: String) {
        assertEquals(prettyPrinted.trimIndent(), getRemoteType(this).prettyPrint())
    }
}