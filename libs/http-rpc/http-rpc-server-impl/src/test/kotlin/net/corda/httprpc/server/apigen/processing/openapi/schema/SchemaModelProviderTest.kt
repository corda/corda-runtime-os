package net.corda.httprpc.server.apigen.processing.openapi.schema

import net.corda.httprpc.server.apigen.models.EndpointParameter
import net.corda.httprpc.server.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.DataFormat
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaEnumModel
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaMapModel
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaPairModel
import net.corda.httprpc.server.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.httprpc.server.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.v5.application.identity.CordaX500Name
import net.corda.v5.base.stream.Cursor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import javax.security.auth.x500.X500Principal

class SchemaModelProviderTest {

    @Test
    fun `build WithBoolean succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = true
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.BOOLEAN, result.type)
        assertEquals(null, result.format)
        assertEquals(true, result.example)
        assertEquals("name", result.name)
        assertEquals("description", result.description)
    }

    @Test
    fun `build WithJavaBoolean succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = true
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.BOOLEAN, result.type)
        assertEquals(null, result.format)
        assertEquals(true, result.example)
    }

    @Test
    fun `build WithX500Principal succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = X500Principal("CN=Common,L=London,O=Org,C=UK")
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.STRING, result.format)
    }

    @Test
    fun `build WithCordaX500Name succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = CordaX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.STRING, result.format)
    }

    @Test
    fun `build WithInteger succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT32, result.format)
        assertEquals(0, result.example)
    }

    @Test
    fun `build WithJavaInteger succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT32, result.format)
        assertEquals(0, result.example)
    }

    @Test
    fun `build WithLong succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1L
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT64, result.format)
    }

    @Test
    fun `build WithJavaLong succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1L
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT64, result.format)
    }

    @Test
    fun `build WithBigInt succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = BigInteger.ONE
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build WithFloat succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1f
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.FLOAT, result.format)
    }

    @Test
    fun `build WithJavaFloat succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1f
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.FLOAT, result.format)
    }

    @Test
    fun `build WithDouble succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1.0
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.DOUBLE, result.format)
    }

    @Test
    fun `build WithJavaDouble succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1.0
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.DOUBLE, result.format)
    }

    @Test
    fun `build WithBigDecimal succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = BigDecimal.ONE
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build WithString succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = "a"
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build WithByteArray succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = ByteArray(0)
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.BYTE, result.format)
    }

    @Test
    fun `build WithInputStream succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.BINARY, result.format)
    }

    @Test
    fun `build WithDate succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = Date()
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.DATETIME, result.format)
    }

    @Test
    fun `build WithDateTime succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = LocalDateTime.now()
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.DATETIME, result.format)
    }

    @Test
    fun `build WithUUID succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = mock<UUID>()
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.UUID, result.format)
    }

    @Test
    fun `build WithEnum succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestEnum.ONE
        val mockParam = mockEndpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals(2, (result as SchemaEnumModel).enum!!.size)
    }

    @Test
    fun `build WithJavaEnum succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestEnum.ONE
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals(2, (result as SchemaEnumModel).enum!!.size)
    }

    @Test
    fun `build WithStringList succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = listOf("a", "b")
        val mockParam = mockEndpointParameter(data::class.javaObjectType, listOf(GenericParameterizedType(String::class.java)))

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.STRING, (result as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build WithListWithNestedStringList succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = mockEndpointParameter(
            List::class.java,
            listOf(GenericParameterizedType(List::class.java, listOf(GenericParameterizedType(String::class.java))))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.ARRAY, (result as SchemaCollectionModel).items!!.type)
        assertEquals(DataType.STRING, (result.items!! as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build WithListWithMoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Iterable and has more than one generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = mockEndpointParameter(
            List::class.java,
            listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.OBJECT, (result as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build WithStringSet succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = setOf("a", "b")
        val mockParam = mockEndpointParameter(data::class.java, listOf(GenericParameterizedType(String::class.java)))

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(true, (result as SchemaCollectionModel).uniqueItems)
        assertEquals(DataType.STRING, result.items!!.type)
    }

    @Test
    fun `build WithSetWithNestedStringToStringMap succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = mockEndpointParameter(
            HashSet::class.java,
            listOf(
                GenericParameterizedType(
                    Map::class.java,
                    listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
                )
            )
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(true, (result as SchemaCollectionModel).uniqueItems)
        assertEquals(DataType.OBJECT, result.items!!.type)
        assertEquals(DataType.STRING, (result.items!! as SchemaMapModel).additionalProperties!!.type)
    }

    @Test
    fun `build WithSetWithMoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Set and has more than one generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = mockEndpointParameter(
            Set::class.java,
            listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(true, (result as SchemaCollectionModel).uniqueItems)
        assertEquals(DataType.OBJECT, result.items!!.type)
    }

    @Test
    fun `build WithMap succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = mapOf("a" to "b")
        val mockParam = mockEndpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.OBJECT, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.STRING, (result as SchemaMapModel).additionalProperties!!.type)
    }

    @Test
    fun `build WithMapWithNestedList succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = mapOf("a" to "b")
        val mockParam = mockEndpointParameter(
            data::class.javaObjectType,
            listOf(
                GenericParameterizedType(String::class.java),
                GenericParameterizedType(
                    List::class.java,
                    listOf(GenericParameterizedType(String::class.java))
                )
            )
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.OBJECT, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.ARRAY, (result as SchemaMapModel).additionalProperties!!.type)
        assertEquals(DataType.STRING, (result.additionalProperties!! as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build WithMapWithMoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Map and has more than two generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = mockEndpointParameter(
            Map::class.java,
            listOf(
                GenericParameterizedType(String::class.java),
                GenericParameterizedType(String::class.java),
                GenericParameterizedType(String::class.java)
            )
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.OBJECT, result.type)
        assertEquals(null, result.format)
        assertEquals(null, (result as SchemaMapModel).additionalProperties)
    }

    @Test
    fun `build WithCustomObject succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestClass()
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals("TestClass", (result as SchemaRefObjectModel).ref)

        with(schemaModelContextHolder.getSchema(ParameterizedClass(data::class.java))!!) {
            assertEquals(2, this.properties.size)
            assertEquals(DataType.STRING, this.properties["a"]!!.type)
            assertEquals("NestedTestClass", (this.properties["b"]!! as SchemaRefObjectModel).ref)
        }
        with(schemaModelContextHolder.getSchema(ParameterizedClass(NestedTestClass::class.java))!!) {
            assertEquals(DataType.OBJECT, this.type)
            assertEquals(2, this.properties.size)
            assertEquals(DataType.ARRAY, this.properties["aa"]!!.type)
            assertEquals(DataType.STRING, (this.properties["aa"]!! as SchemaCollectionModel).items!!.type)
            assertEquals(DataType.INTEGER, this.properties["bb"]!!.type)
        }
    }

    @Test
    fun `build WithPair succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = mockEndpointParameter(
            Pair::class.java,
            listOf(
                GenericParameterizedType(Int::class.java, emptyList()),
                GenericParameterizedType(String::class.java, emptyList())
            )
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        assertNull(schemaModelContextHolder.getSchema(ParameterizedClass(Pair::class.java)))
        with(result as SchemaPairModel) {
            assertEquals(DataType.INTEGER, this.properties["first"]!!.type)
            assertEquals(DataType.STRING, this.properties["second"]!!.type)
        }
    }

    @Test
    fun `build WithCustomObjectAndContextAlreadyPopulated succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestClass()
        val mockParam = mockEndpointParameter(data::class.javaObjectType)
        provider.toSchemaModel(mockParam)

        assertNotNull(schemaModelContextHolder.getSchema(ParameterizedClass(data::class.java)))

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals("TestClass", (result as SchemaRefObjectModel).ref)

        with(schemaModelContextHolder.getSchema(ParameterizedClass(data::class.java))!!) {
            assertEquals(2, this.properties.size)
            assertEquals(DataType.STRING, this.properties["a"]!!.type)
            assertEquals("NestedTestClass", (this.properties["b"]!! as SchemaRefObjectModel).ref)
        }
        with(schemaModelContextHolder.getSchema(ParameterizedClass(NestedTestClass::class.java))!!) {
            assertEquals(DataType.OBJECT, this.type)
            assertEquals(2, this.properties.size)
            assertEquals(DataType.ARRAY, this.properties["aa"]!!.type)
            assertEquals(DataType.INTEGER, this.properties["bb"]!!.type)
        }
    }

    @Test
    fun `build WithCustomJavaObject succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = net.corda.httprpc.server.apigen.processing.openapi.schema.TestClass()
        val mockParam = mockEndpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals("TestClass", (result as SchemaRefObjectModel).ref)

        with(schemaModelContextHolder.getSchema(ParameterizedClass(data::class.java))!!) {
            assertEquals(2, this.properties.size)
            assertEquals(DataType.STRING, this.properties["a"]!!.type)
            assertEquals(
                "TestNestedClass",
                (this.properties["b"]!! as SchemaRefObjectModel).ref
            )
        }
        with(schemaModelContextHolder.getSchema(ParameterizedClass(TestNestedClass::class.java))!!) {
            assertEquals(DataType.OBJECT, this.type)
            assertEquals(2, this.properties.size)
            assertEquals(DataType.ARRAY, this.properties["aa"]!!.type)
            assertEquals(DataType.INTEGER, this.properties["bb"]!!.type)
        }
    }

    @Test
    fun `build WithDurableReturnResult succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = DurableReturnResult(listOf(testPositionedValue("a")), 1)
        val mockParam = mockEndpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java, emptyList()))
        )

        val result = provider.toSchemaModel(mockParam)
        assertNull(result.format)
        assertNull(result.type)
        result as SchemaRefObjectModel
        assertEquals("DurableReturnResult of String", result.ref)
    }

    @Test
    fun `build WithFiniteDurableReturnResult succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = FiniteDurableReturnResult(listOf(testPositionedValue("a")), 1, false)
        val mockParam = mockEndpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java, emptyList()))
        )

        val result = provider.toSchemaModel(mockParam)


        assertNull(result.type)
        assertNull(result.format)
        result as SchemaRefObjectModel
        assertEquals("FiniteDurableReturnResult of String", result.ref)
    }

    @Test
    fun `build WithMultiple Generics types succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val result = provider.toSchemaModel(
            ParameterizedClass(
                TestClass::class.java,
                listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(Int::class.java))
            )
        )

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals("TestClass of String int", (result as SchemaRefObjectModel).ref)
    }

    @Test
    fun `build WithSame Class name in different packages succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val result =
            provider.toSchemaModel(ParameterizedClass(net.corda.httprpc.server.apigen.processing.DurableStreamsMethodInvoker::class.java))
        assertEquals("DurableStreamsMethodInvoker", (result as SchemaRefObjectModel).ref)

        val result2 = provider.toSchemaModel(ParameterizedClass(DurableStreamsMethodInvoker::class.java))
        assertEquals("DurableStreamsMethodInvoker 1", (result2 as SchemaRefObjectModel).ref)
    }

    @Test
    fun `build WithSame Class succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val result = provider.toSchemaModel(
            ParameterizedClass(
                DurableStreamsMethodInvoker::class.java,
                listOf(GenericParameterizedType(String::class.java))
            )
        )
        assertEquals("DurableStreamsMethodInvoker of String", (result as SchemaRefObjectModel).ref)

        val result2 = provider.toSchemaModel(
            ParameterizedClass(
                DurableStreamsMethodInvoker::class.java,
                listOf(GenericParameterizedType(Date::class.java))
            )
        )
        assertEquals("DurableStreamsMethodInvoker of Date", (result2 as SchemaRefObjectModel).ref)
    }

    class DurableStreamsMethodInvoker()

    class NestedTestClass(
        val aa: List<String> = listOf("aa"),
        val bb: Int = 1,
        private val cc: String = "cc"
    )

    class TestClass(
        val a: String = "a",
        val b: NestedTestClass = NestedTestClass(),
        private val c: String = "c"
    )

    private fun mockEndpointParameter(clazz: Class<*>, parameterizedTypes: List<GenericParameterizedType> = emptyList()) =
        mock<EndpointParameter>().also {
            doReturn(clazz).whenever(it).classType
            doReturn(parameterizedTypes).whenever(it).parameterizedTypes
            doReturn("name").whenever(it).name
            doReturn("description").whenever(it).description
        }

    private enum class TestEnum {
        ONE, TWO
    }

    private fun <T> testPositionedValue(value: T): Cursor.PollResult.PositionedValue<T> {
        return object : Cursor.PollResult.PositionedValue<T> {
            override val position: Long
                get() = 1
            override val value: T
                get() = value
        }
    }
}