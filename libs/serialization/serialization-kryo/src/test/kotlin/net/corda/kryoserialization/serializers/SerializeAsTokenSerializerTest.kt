package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.cipher.suite.internal.BasicHashingServiceImpl
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.CheckpointSerializer
import net.corda.kryoserialization.DefaultWhitelist
import net.corda.kryoserialization.KRYO_CHECKPOINT_CONTEXT
import net.corda.kryoserialization.KryoCheckpointSerializerBuilder
import net.corda.kryoserialization.impl.CheckpointSerializeAsTokenContextImpl
import net.corda.kryoserialization.impl.withTokenContext
import net.corda.kryoserialization.osgi.SandboxClassResolver
import net.corda.sandbox.SandboxGroup
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
import org.objenesis.strategy.SerializingInstantiatorStrategy

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

    private val serializer = createCheckpointSerializer()
    private lateinit var context: CheckpointSerializationContext

    private val service1 = MyService1()
    private val service2 = MyService2()
    private val duplicateService1 = DuplicateService("please serialize me")
    private val duplicateService2 = DuplicateService("please please serialize me")

    private val classInfoService = mock<ClassInfoService>()
    private val sandboxGroup = mock<SandboxGroup>()

    @BeforeEach
    fun setup() {
        service1.count = 0
        context = createCheckpointContext()
    }

    @Test
    fun `SerializeAsToken class serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service1, context)
        val output = serializer.deserialize(bytes, MyService1::class.java, context)
        assertSame(service1, output)
        assertEquals(2, service1.count)
    }

    @Test
    fun `SerializeAsToken class serializes as a token and must be deserialized into its original class type`() {
        val bytes = serializer.serialize(service1, context)
        assertNotEquals(MyService2::class, serializer.deserialize(bytes, MyService2::class.java, context)::class)
    }

    @Test
    fun `SerializeAsToken classes serialize and deserialize the same class type into multiple instances when the tokens are unique`() {
        val bytes = serializer.serialize(duplicateService1, context)
        assertSame(duplicateService1, serializer.deserialize(bytes, DuplicateService::class.java, context))
        assertNotSame(duplicateService2, serializer.deserialize(bytes, DuplicateService::class.java, context))
    }

    @Test
    fun `Repeated serialization of a SerializeAsToken class caused regeneration of the SerializationToken`() {
        val bytes1 = serializer.serialize(service1, context)
        val output1 = serializer.deserialize(bytes1, MyService1::class.java, context)
        assertSame(service1, output1)
        assertEquals(2, service1.count)

        val bytes2 = serializer.serialize(service1, context)
        val output2 = serializer.deserialize(bytes2, MyService1::class.java, context)
        assertSame(service1, output2)
        assertEquals(3, service1.count)
    }

    private fun createCheckpointContext(): CheckpointSerializationContext {
        return KRYO_CHECKPOINT_CONTEXT.withTokenContext(
            CheckpointSerializeAsTokenContextImpl(
                listOf(service1, service2, duplicateService1, duplicateService2),
                serializer,
                KRYO_CHECKPOINT_CONTEXT
            )
        ).withClassInfoService(classInfoService).withSandboxGroup(sandboxGroup)
    }

    private fun createCheckpointSerializer(): CheckpointSerializer {
        val hashingService = BasicHashingServiceImpl()
        val kryo = Kryo(SandboxClassResolver(classInfoService, sandboxGroup, hashingService), MapReferenceResolver())
        kryo.instantiatorStrategy = SerializingInstantiatorStrategy()
        val serializerBuilder = KryoCheckpointSerializerBuilder({ kryo }, DefaultWhitelist, hashingService)
        return serializerBuilder.build()
    }
}