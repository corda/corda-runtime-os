package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import net.corda.httprpc.server.apigen.processing.openapi.schema.TestNestedClass
import net.corda.httprpc.server.impl.apigen.models.EndpointParameter
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataFormat
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaEnumModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaMapModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPairModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import net.corda.httprpc.server.impl.apigen.processing.streams.DurableReturnResult
import net.corda.httprpc.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.httprpc.durablestream.api.Cursor
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import javax.security.auth.x500.X500Principal
import net.corda.httprpc.HttpFileUpload

class SchemaModelProviderTest {

    @Test
    fun `build with Boolean succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = true
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.BOOLEAN, result.type)
        assertEquals(null, result.format)
        assertEquals(true, result.example)
        assertEquals("name", result.name)
        assertEquals("description", result.description)
    }

    @Test
    fun `build with Java Boolean succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = true
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.BOOLEAN, result.type)
        assertEquals(null, result.format)
        assertEquals(true, result.example)
    }

    @Test
    fun `build with X500Principal succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = X500Principal("CN=Common,L=London,O=Org,C=UK")
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.STRING, result.format)
    }

    @Test
    fun `build with MemberX500Name succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.STRING, result.format)
    }

    @Test
    fun `build with Integer succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT32, result.format)
        assertEquals(0, result.example)
    }

    @Test
    fun `build with Java Integer succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT32, result.format)
        assertEquals(0, result.example)
    }

    @Test
    fun `build with Long succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1L
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT64, result.format)
    }

    @Test
    fun `build with Java Long succeeds`() {
        val provider = DefaultSchemaModelProvider(SchemaModelContextHolder())
        val data = 1L
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(DataFormat.INT64, result.format)
    }

    @Test
    fun `build with BigInt succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = BigInteger.ONE
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.INTEGER, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build with Float succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1f
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.FLOAT, result.format)
    }

    @Test
    fun `build with Java Float succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1f
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.FLOAT, result.format)
    }

    @Test
    fun `build with Double succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1.0
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.DOUBLE, result.format)
    }

    @Test
    fun `build with Java Double succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = 1.0
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(DataFormat.DOUBLE, result.format)
    }

    @Test
    fun `build with BigDecimal succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = BigDecimal.ONE
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.NUMBER, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build with String succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = "a"
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(null, result.format)
    }

    @Test
    fun `build with ByteArray succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = ByteArray(0)
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.BYTE, result.format)
    }

    @Test
    fun `build with InputStream succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.BINARY, result.format)
    }

    @Test
    fun `build with HttpFileUpload succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = HttpFileUpload("content".byteInputStream(), "binary", ".png", "test", 123L)
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.BINARY, result.format)
    }

    @Test
    fun `build with Date succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = Date()
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.DATETIME, result.format)
    }

    @Test
    fun `build with DateTime succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = LocalDateTime.now()
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.DATETIME, result.format)
    }

    @Test
    fun `build with UUID succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = UUID.randomUUID()
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.STRING, result.type)
        assertEquals(DataFormat.UUID, result.format)
    }

    @Test
    fun `build with Enum succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestEnum.ONE
        val mockParam = endpointParameter(data::class.java)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals(2, (result as SchemaEnumModel).enum!!.size)
    }

    @Test
    fun `build with Java Enum succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestEnum.ONE
        val mockParam = endpointParameter(data::class.javaObjectType)

        val result = provider.toSchemaModel(mockParam)

        assertEquals(null, result.type)
        assertEquals(null, result.format)
        assertEquals(2, (result as SchemaEnumModel).enum!!.size)
    }

    @Test
    fun `build with String List succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = listOf("a", "b")
        val mockParam = endpointParameter(data::class.javaObjectType, listOf(GenericParameterizedType(String::class.java)))

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.STRING, (result as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build with List with Nested String List succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = endpointParameter(
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
    fun `build with List with MoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Iterable and has more than one generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = endpointParameter(
            List::class.java,
            listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.OBJECT, (result as SchemaCollectionModel).items!!.type)
    }

    @Test
    fun `build with String Set succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = setOf("a", "b")
        val mockParam = endpointParameter(data::class.java, listOf(GenericParameterizedType(String::class.java)))

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.ARRAY, result.type)
        assertEquals(null, result.format)
        assertEquals(true, (result as SchemaCollectionModel).uniqueItems)
        assertEquals(DataType.STRING, result.items!!.type)
    }

    @Test
    fun `build with Set with NestedStringToStringMap succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = endpointParameter(
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
    fun `build with Set with MoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Set and has more than one generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = endpointParameter(
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
    fun `build with Map succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = mapOf("a" to "b")
        val mockParam = endpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java), GenericParameterizedType(String::class.java))
        )

        val result = provider.toSchemaModel(mockParam)

        assertEquals(DataType.OBJECT, result.type)
        assertEquals(null, result.format)
        assertEquals(DataType.STRING, (result as SchemaMapModel).additionalProperties!!.type)
    }

    @Test
    fun `build with Map with NestedList succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = mapOf("a" to "b")
        val mockParam = endpointParameter(
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
    fun `build with Map with MoreGenerics succeedsButHasNoItems`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        // can happen if a custom class extends Map and has more than two generics. In this case, we don't know which generic type
        // would represent the item class
        val mockParam = endpointParameter(
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
    fun `build with CustomObject succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestClass()
        val mockParam = endpointParameter(data::class.javaObjectType)

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
    fun `build with Pair succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val mockParam = endpointParameter(
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
    fun `build with custom object and context already populated succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = TestClass()
        val mockParam = endpointParameter(data::class.javaObjectType)
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
    fun `build with custom java object succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = net.corda.httprpc.server.apigen.processing.openapi.schema.TestClass()
        val mockParam = endpointParameter(data::class.javaObjectType)

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
    fun `build with DurableReturnResult succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = DurableReturnResult(listOf(testPositionedValue("a")), 1)
        val mockParam = endpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java, emptyList()))
        )

        val result = provider.toSchemaModel(mockParam)
        assertNull(result.format)
        assertNull(result.type)
        result as SchemaRefObjectModel
        assertEquals("DurableReturnResult_of_String", result.ref)
    }

    @Test
    fun `build with FiniteDurableReturnResult succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val data = FiniteDurableReturnResult(listOf(testPositionedValue("a")), 1, false)
        val mockParam = endpointParameter(
            data::class.javaObjectType,
            listOf(GenericParameterizedType(String::class.java, emptyList()))
        )

        val result = provider.toSchemaModel(mockParam)


        assertNull(result.type)
        assertNull(result.format)
        result as SchemaRefObjectModel
        assertEquals("FiniteDurableReturnResult_of_String", result.ref)
    }

    @Test
    fun `build with multiple generics types succeeds`() {
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
        assertEquals("TestClass_of_String_int", (result as SchemaRefObjectModel).ref)
    }

    @Test
    fun `build with same class name in different packages succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val result =
            provider.toSchemaModel(
                    ParameterizedClass(net.corda.httprpc.server.impl.apigen.processing.DurableStreamsMethodInvoker::class.java))
        assertEquals("DurableStreamsMethodInvoker", (result as SchemaRefObjectModel).ref)

        val result2 = provider.toSchemaModel(ParameterizedClass(DurableStreamsMethodInvoker::class.java))
        assertEquals("DurableStreamsMethodInvoker_1", (result2 as SchemaRefObjectModel).ref)
    }

    @Test
    fun `build with same class succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)

        val result = provider.toSchemaModel(
            ParameterizedClass(
                DurableStreamsMethodInvoker::class.java,
                listOf(GenericParameterizedType(String::class.java))
            )
        )
        assertEquals("DurableStreamsMethodInvoker_of_String", (result as SchemaRefObjectModel).ref)

        val result2 = provider.toSchemaModel(
            ParameterizedClass(
                DurableStreamsMethodInvoker::class.java,
                listOf(GenericParameterizedType(Date::class.java))
            )
        )
        assertEquals("DurableStreamsMethodInvoker_of_Date", (result2 as SchemaRefObjectModel).ref)
    }

    class DurableStreamsMethodInvoker

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

    private fun endpointParameter(clazz: Class<*>, parameterizedTypes: List<GenericParameterizedType> = emptyList()): EndpointParameter {
        return EndpointParameter(
            classType = clazz,
            parameterizedTypes = parameterizedTypes,
            name = "name",
            description = "description",
            default = "default",
            id = "id",
            nullable = true,
            required = true,
            type = ParameterType.PATH
        )
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