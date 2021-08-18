package net.corda.internal.serialization.amqp

import com.google.common.reflect.TypeToken
import net.corda.internal.serialization.MAX_TYPE_PARAM_DEPTH
import net.corda.internal.serialization.model.TypeIdentifier
import org.apache.qpid.proton.amqp.UnsignedShort
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.io.NotSerializableException
import java.lang.reflect.Type
import java.time.LocalDateTime
import java.util.UUID
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

interface TestCommandData

data class TestCommand<T : TestCommandData>(val value: T, val signers: List<String>)

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AMQPTypeIdentifierParserTests {

    @Test
    fun `primitives and arrays`() {
        assertParseResult<Int>("int")
        assertParseResult<IntArray>("int[p]")
        assertParseResult<Array<Int>>("int[]")
        assertParseResult<Array<IntArray>>("int[p][]")
        assertParseResult<Array<Array<Int>>>("int[][]")
        assertParseResult<ByteArray>("binary")
        assertParseResult<Array<ByteArray>>("binary[]")
        assertParseResult<Array<UnsignedShort>>("ushort[]")
        assertParseResult<Array<Array<String>>>("string[][]")
        assertParseResult<UUID>("uuid")
        assertParseResult<Date>("timestamp")

        // We set a limit to the depth of arrays-of-arrays-of-arrays...
        assertFailsWith<IllegalTypeNameParserStateException> {
            AMQPTypeIdentifierParser.parse("string" + "[]".repeat(33))
        }
    }

    @Test
	fun `unparameterised types`() {
        assertParseResult<LocalDateTime>("java.time.LocalDateTime")
        assertParseResult<Array<LocalDateTime>>("java.time.LocalDateTime[]")
        assertParseResult<Array<Array<LocalDateTime>>>("java.time.LocalDateTime[][]")
    }

    interface WithParameter<T> {
        val value: T
    }

    interface WithParameters<P, Q> {
        val p: Array<out P>
        val q: WithParameter<Array<Q>>
    }

    @Test
	fun `parameterised types, nested, with arrays`() {
        assertParsesTo<WithParameters<IntArray, WithParameter<Array<WithParameters<Array<Array<Date>>, UUID>>>>>(
                "WithParameters<int[], WithParameter<WithParameters<Date[][], UUID>[]>>"
        )

        // We set a limit to the maximum depth of nested type parameters.
        assertFailsWith<IllegalTypeNameParserStateException> {
            AMQPTypeIdentifierParser.parse("WithParameter<".repeat(33) + ">".repeat(33))
        }
    }

    @Test
	fun `compatibility test`() {
        assertParsesCompatibly<Int>()
        assertParsesCompatibly<IntArray>()
        assertParsesCompatibly<Array<Int>>()
        assertParsesCompatibly<List<Int>>()
        assertParsesTo<WithParameter<*>>("WithParameter<Object>")
        assertParsesCompatibly<WithParameter<Int>>()
        assertParsesCompatibly<Array<out WithParameter<Int>>>()
        assertParsesCompatibly<WithParameters<IntArray, WithParameter<Array<WithParameters<Array<Array<Date>>, UUID>>>>>()
    }

    // Old tests for DeserializedParameterizedType
    @Test
	fun `test nested`() {
        verify(" java.util.Map < java.util.Map< java.lang.String, java.lang.Integer >, java.util.Map < java.lang.Long , java.lang.String > >")
    }

    @Test
	fun `test simple`() {
        verify("java.util.List<java.lang.String>")
    }

    @Test
	fun `test multiple args`() {
        verify("java.util.Map<java.lang.String,java.lang.Integer>")
    }

    @Test
	fun `test trailing whitespace`() {
        verify("java.util.Map<java.lang.String, java.lang.Integer> ")
    }

    @Test
	fun `test list of commands`() {
        verify("java.util.List<net.corda.internal.serialization.amqp.TestCommand<net.corda.internal.serialization.amqp.TestCommand<net.corda.internal.serialization.amqp.TestCommandData>>>")
    }

    @Test
    fun `test trailing text`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<java.lang.String, java.lang.Integer>foo")
        }
    }

    @Test
    fun `test trailing comma`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<java.lang.String, java.lang.Integer,>")
        }
    }

    @Test
    fun `test leading comma`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<,java.lang.String, java.lang.Integer>")
        }
    }

    @Test
    fun `test middle comma`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<,java.lang.String,, java.lang.Integer>")
        }
    }

    @Test
    fun `test trailing close`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<java.lang.String, java.lang.Integer>>")
        }
    }

    @Test
    fun `test empty params`() {
        assertThrows<NotSerializableException> {
            verify("java.util.Map<>")
        }
    }

    @Test
    fun `test mid whitespace`() {
        assertThrows<NotSerializableException> {
            verify("java.u til.List<java.lang.String>")
        }
    }

    @Test
    fun `test mid whitespace2`() {
        assertThrows<NotSerializableException> {
            verify("java.util.List<java.l ng.String>")
        }
    }

    @Test
    fun `test wrong number of parameters`() {
        assertThrows<NotSerializableException> {
            verify("java.util.List<java.lang.String, java.lang.Integer>")
        }
    }

    @Test
	fun `test no parameters`() {
        verify("java.lang.String")
    }

    @Test
    fun `test parameters on non-generic type`() {
        assertThrows<NotSerializableException> {
            verify("java.lang.String<java.lang.Integer>")
        }
    }

    @Test
    fun `test excessive nesting`() {
        var nested = "java.lang.Integer"
        for (i in 1..MAX_TYPE_PARAM_DEPTH) {
            nested = "java.util.List<$nested>"
        }
        assertThrows<NotSerializableException> {
            verify(nested)
        }
    }

    private inline fun <reified T> assertParseResult(typeString: String) {
        assertEquals(TypeIdentifier.forGenericType(typeOf<T>()), AMQPTypeIdentifierParser.parse(typeString))
    }

    private inline fun <reified T> typeOf() = object : TypeToken<T>() {}.type

    private inline fun <reified T> assertParsesCompatibly() = assertParsesCompatibly(typeOf<T>())

    private fun assertParsesCompatibly(type: Type) {
        assertParsesTo(type, TypeIdentifier.forGenericType(type).prettyPrint())
    }

    private inline fun <reified T> assertParsesTo(expectedIdentifierPrettyPrint: String) {
        assertParsesTo(typeOf<T>(), expectedIdentifierPrettyPrint)
    }

    private fun assertParsesTo(type: Type, expectedIdentifierPrettyPrint: String) {
        val nameForType = AMQPTypeIdentifiers.nameForType(type)
        val parsedIdentifier = AMQPTypeIdentifierParser.parse(nameForType)
        assertEquals(expectedIdentifierPrettyPrint, parsedIdentifier.prettyPrint())
    }


    private fun normalise(string: String): String {
        return string.replace(" ", "")
    }

    private fun verify(typeName: String) {
        val type = AMQPTypeIdentifierParser.parse(typeName).getLocalType()
        assertEquals(normalise(typeName), normalise(type.typeName))
    }
}