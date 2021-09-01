package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import net.corda.cipher.suite.internal.BasicHashingServiceImpl
import net.corda.classinfo.ClassInfoService
import net.corda.kryoserialization.CheckpointSerializationContext
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.KryoCheckpointSerializerBuilderImpl
import net.corda.kryoserialization.impl.CheckpointSerializationContextImpl
import net.corda.kryoserialization.resolver.SandboxClassResolver
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.CheckpointSerializer
import net.corda.v5.base.types.sequence
import net.corda.v5.serialization.SerializeAsTokenContext
import net.corda.v5.serialization.SingletonSerializationToken
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.objenesis.strategy.SerializingInstantiatorStrategy

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
        val bytes = serializer.serialize(service1)
        val output = serializer.deserialize(bytes.sequence(), MyService1::class.java)
        assertSame(service1, output)
        assertEquals(1, service1.count)
    }

    @Test
    fun `SingletonSerializeAsToken class serializes as a token and must be deserialized into its original class type`() {
        val bytes = serializer.serialize(service1)
        assertNotEquals(MyService2::class, serializer.deserialize(bytes.sequence(), MyService2::class.java)::class)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `SingletonSerializeAsToken class with custom tokenName serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service4)
        val output = serializer.deserialize(bytes.sequence(), MyService4::class.java)
        assertSame(service4, output)
    }

    @Test
    @Suppress("MaxLineLength")
    fun `SingletonSerializeAsToken class with custom toToken serializes as a token and returns the original tokenized instance when deserialized`() {
        val bytes = serializer.serialize(service5)
        val output = serializer.deserialize(bytes.sequence(), MyService5::class.java)
        assertSame(service5, output)
    }

    @Test
    fun `Repeated serialization of a SingletonSerializeAsToken class uses the stored token`() {
        val bytes1 = serializer.serialize(service1)
        val output1 = serializer.deserialize(bytes1.sequence(), MyService1::class.java)
        assertSame(service1, output1)
        assertEquals(1, service1.count)

        val bytes2 = serializer.serialize(service1)
        val output2 = serializer.deserialize(bytes2.sequence(), MyService1::class.java)
        assertSame(service1, output2)
        assertEquals(1, service1.count)
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
    }

    private fun createCheckpointSerializer(): CheckpointSerializer {
        val hashingService = BasicHashingServiceImpl()
        val kryo = Kryo(SandboxClassResolver(classInfoService, sandboxGroup, hashingService), MapReferenceResolver())
        kryo.instantiatorStrategy = SerializingInstantiatorStrategy()
        return KryoCheckpointSerializerBuilderImpl(mock(), mock())
            .newCheckpointSerializer(sandboxGroup)
            .build()
    }
}
