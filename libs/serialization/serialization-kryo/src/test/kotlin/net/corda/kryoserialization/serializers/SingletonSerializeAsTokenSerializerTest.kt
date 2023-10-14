package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.esotericsoftware.kryo.util.Pool
import net.corda.kryoserialization.CordaKryoException
import net.corda.kryoserialization.DefaultKryoCustomizer
import net.corda.kryoserialization.KryoCheckpointSerializer
import net.corda.kryoserialization.resolver.CordaClassResolver
import net.corda.kryoserialization.testkit.createCheckpointSerializer
import net.corda.kryoserialization.testkit.mockSandboxGroup
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class SingletonSerializeAsTokenSerializerTest {

    class Tester(val someInt: Int) : SingletonSerializeAsToken

    @Test
    fun `singleton serializer returns the correct instance back`() {
        val instance = Tester(1)
        val serializer = createCheckpointSerializer(singletonInstances = listOf(instance))

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }

    @Test
    fun `singleton serializer throws on serialize when instance not registered`() {
        val instance = Tester(1)
        val sandboxGroup = mockSandboxGroup(setOf(Tester::class.java))

        val serializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                mapOf(SingletonSerializeAsToken::class.java to SingletonSerializeAsTokenSerializer(emptyMap())),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )

        assertThatExceptionOfType(CordaKryoException::class.java).isThrownBy {
            serializer.serialize(instance)
        }
    }

    @Disabled("Not sure we need this as the FieldSerializer will sort us out?")
    @Test
    fun `singleton serializer throws on deserialize when instance not registered`() {
        val instance = Tester(1)
        val serializer = createCheckpointSerializer(singletonInstances = listOf(instance))

        val sandboxGroup = mockSandboxGroup(setOf(Tester::class.java))
        val deserializer = KryoCheckpointSerializer(
            DefaultKryoCustomizer.customize(
                Kryo(CordaClassResolver(sandboxGroup), MapReferenceResolver()),
                emptyMap(),
                ClassSerializer(sandboxGroup)
            ).toPool()
        )

        val bytes = serializer.serialize(instance)
        assertThatExceptionOfType(CordaKryoException::class.java).isThrownBy {
            deserializer.deserialize(bytes, instance::class.java)
        }
    }

    private fun Kryo.toPool(): Pool<Kryo> {
        return object : Pool<Kryo>(true, false, 4) {
            override fun create(): Kryo {
                return this@toPool
            }
        }
    }
}
