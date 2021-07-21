package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.cipher.suite.internal.BasicHashingServiceImpl
import net.corda.classinfo.ClassInfoService
import net.corda.internal.serialization.CheckpointSerializationContext
import net.corda.internal.serialization.CheckpointSerializer
import net.corda.internal.serialization.DefaultWhitelist
import net.corda.internal.serialization.KRYO_CHECKPOINT_CONTEXT
import net.corda.kryoserialization.CheckpointSerializeAsTokenContextImpl
import net.corda.kryoserialization.KryoCheckpointSerializerBuilder
import net.corda.kryoserialization.osgi.SandboxClassResolver
import net.corda.kryoserialization.withTokenContext
import net.corda.sandbox.SandboxGroup
import net.corda.v5.serialization.SerializeAsTokenContext
import net.corda.v5.serialization.SingletonSerializationToken
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.objenesis.strategy.SerializingInstantiatorStrategy
import kotlin.test.assertSame

class SingletonSerializeAsTokenSerializerTest {

    private class MyService1 : SingletonSerializeAsToken {

        var count = 0

        override fun toToken(context: SerializeAsTokenContext): SingletonSerializationToken {
            count++
            return super.toToken(context)
        }
    }

    private class MyService2 : SingletonSerializeAsToken
    private class MyService3 : SingletonSerializeAsToken

    private class MyService4 : SingletonSerializeAsToken {
        override val tokenName = "This is my token name"
    }

    private class MyService5 : SingletonSerializeAsToken {
        override fun toToken(context: SerializeAsTokenContext): SingletonSerializationToken {
            return SingletonSerializationToken.singletonSerializationToken("I've decided to override this function instead")
        }
    }

    private val serializer = createCheckpointSerializer()
    private lateinit var context: CheckpointSerializationContext

    private val service1 = MyService1()
    private val service2 = MyService2()
    private val service3 = MyService3()
    private val service4 = MyService4()
    private val service5 = MyService5()

    private val classInfoService = mock<ClassInfoService>()
    private val sandboxGroup = mock<SandboxGroup>()

    @BeforeEach
    fun setup() {
        service1.count = 0
        context = createCheckpointContext()
    }

    @Test
    fun `SingletonSerializeAsToken class serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service1, context)
        val output = serializer.deserialize(bytes, MyService1::class.java, context)
        assertSame(service1, output)
        assertEquals(1, service1.count)
    }

    @Test
    fun `SingletonSerializeAsToken class serializes as a token and must be deserialized into its original class type`() {
        val bytes = serializer.serialize(service1, context)
        assertNotEquals(MyService2::class, serializer.deserialize(bytes, MyService2::class.java, context)::class)
    }

    @Test
    fun `SingletonSerializeAsToken class with custom tokenName serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service4, context)
        val output = serializer.deserialize(bytes, MyService4::class.java, context)
        assertSame(service4, output)
    }

    @Test
    fun `SingletonSerializeAsToken class with custom toToken serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service5, context)
        val output = serializer.deserialize(bytes, MyService5::class.java, context)
        assertSame(service5, output)
    }

    @Test
    fun `Repeated serialization of a SingletonSerializeAsToken class uses the stored token`() {
        val bytes1 = serializer.serialize(service1, context)
        val output1 = serializer.deserialize(bytes1, MyService1::class.java, context)
        assertSame(service1, output1)
        assertEquals(1, service1.count)

        val bytes2 = serializer.serialize(service1, context)
        val output2 = serializer.deserialize(bytes2, MyService1::class.java, context)
        assertSame(service1, output2)
        assertEquals(1, service1.count)
    }

    private fun createCheckpointContext(): CheckpointSerializationContext {
        return KRYO_CHECKPOINT_CONTEXT.withTokenContext(
            CheckpointSerializeAsTokenContextImpl(
                listOf(service1, service2, service3, service4, service5),
                serializer,
                KRYO_CHECKPOINT_CONTEXT
            )
        ).withSandboxGroup(sandboxGroup).withClassInfoService(classInfoService)
    }

    private fun createCheckpointSerializer(): CheckpointSerializer {
        val hashingService = BasicHashingServiceImpl()
        val kryo = Kryo(SandboxClassResolver(classInfoService, sandboxGroup, hashingService), MapReferenceResolver())
        kryo.instantiatorStrategy = SerializingInstantiatorStrategy()
        val serializerBuilder = KryoCheckpointSerializerBuilder({ kryo }, DefaultWhitelist, hashingService)
        return serializerBuilder.build()
    }
}