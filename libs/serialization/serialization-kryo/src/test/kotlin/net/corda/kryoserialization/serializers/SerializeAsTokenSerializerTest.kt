package net.corda.kryoserialization.serializers

import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerBuilderImpl
import net.corda.kryoserialization.impl.CheckpointSerializationContextImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.types.sequence
import net.corda.v5.serialization.SerializationToken
import net.corda.v5.serialization.SerializeAsToken
import net.corda.v5.serialization.SerializeAsTokenContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SerializeAsTokenSerializerTest {

    private class MySerializationToken(val name: String) : SerializationToken {
        override fun fromToken(context: SerializeAsTokenContext): Any {
            return context.fromIdentifier(name)
        }
    }

    private class MyService1 : SerializeAsToken {

        var count = 0

        val token = MySerializationToken("hello there")

        override fun toToken(context: SerializeAsTokenContext): SerializationToken {
            count++
            context.withIdentifier(token.name, this)
            return token
        }
    }

    private class MyService2 : SerializeAsToken {

        val token = MySerializationToken("i'm a token")

        override fun toToken(context: SerializeAsTokenContext): SerializationToken {
            context.withIdentifier(token.name, this)
            return token
        }
    }

    private class DuplicateService(private val name: String) : SerializeAsToken {

        val token = MySerializationToken(name)

        override fun toToken(context: SerializeAsTokenContext): SerializationToken {
            context.withIdentifier(name, this)
            return token
        }
    }

    private val service1 = MyService1()
    private val service2 = MyService2()
    private val duplicateService1 = DuplicateService("please serialize me")
    private val duplicateService2 = DuplicateService("please please serialize me")

    private val classInfoService = mock<ClassInfoService>()
    private val sandboxGroup = mock<SandboxGroup>()

    private val serializer = createCheckpointSerializer()
    private lateinit var context: CheckpointSerializationContext

    @BeforeEach
    fun setup() {
        service1.count = 0
        context = createCheckpointContext()
    }

    @Test
    fun `SerializeAsToken class serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service1)
        val output = serializer.deserialize(bytes.sequence(), MyService1::class.java)
        assertSame(service1, output)
        assertEquals(2, service1.count)
    }

    @Test
    fun `SerializeAsToken class serializes as a token and must be deserialized into its original class type`() {
        val bytes = serializer.serialize(service1)
        assertNotEquals(MyService2::class, serializer.deserialize(bytes.sequence(), MyService2::class.java)::class)
    }

    @Test
    fun `SerializeAsToken classes serialize and deserialize the same class type into multiple instances when the tokens are unique`() {
        val bytes = serializer.serialize(duplicateService1)
        assertSame(duplicateService1, serializer.deserialize(bytes.sequence(), DuplicateService::class.java))
        assertNotSame(duplicateService2, serializer.deserialize(bytes.sequence(), DuplicateService::class.java))
    }

    @Test
    fun `Repeated serialization of a SerializeAsToken class caused regeneration of the SerializationToken`() {
        val bytes1 = serializer.serialize(service1)
        val output1 = serializer.deserialize(bytes1.sequence(), MyService1::class.java)
        assertSame(service1, output1)
        assertEquals(2, service1.count)

        val bytes2 = serializer.serialize(service1)
        val output2 = serializer.deserialize(bytes2.sequence(), MyService1::class.java)
        assertSame(service1, output2)
        assertEquals(3, service1.count)
    }

    private fun createCheckpointContext(): CheckpointSerializationContext {
        return CheckpointSerializationContextImpl(
            null,
            KryoCheckpointSerializer::class.java.classLoader,
            emptyMap(),
            true,
            classInfoService,
            sandboxGroup
        )
//            CheckpointSerializeAsTokenContextImpl(
//                listOf(service1, service2, duplicateService1, duplicateService2),
//                serializer,
//                KRYO_CHECKPOINT_CONTEXT
//            )
    }

    private fun createCheckpointSerializer(): CheckpointSerializer {
        return KryoCheckpointSerializerBuilderImpl(mock(), mock())
            .newCheckpointSerializer(sandboxGroup)
            .build()
    }
}
