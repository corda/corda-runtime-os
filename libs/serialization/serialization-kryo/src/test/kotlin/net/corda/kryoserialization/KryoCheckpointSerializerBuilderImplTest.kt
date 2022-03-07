package net.corda.kryoserialization

import net.corda.kryoserialization.TestClass.Companion.TEST_INT
import net.corda.kryoserialization.TestClass.Companion.TEST_STRING
import net.corda.kryoserialization.impl.KryoCheckpointSerializerBuilderImpl
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.checkpoint.CheckpointSerializerBuilder
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPrivateCrtKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import java.security.PrivateKey
import java.security.PublicKey

internal class KryoCheckpointSerializerBuilderImplTest {

    @Test
    fun `builder builds a serializer correctly`() {
        val sandboxGroup: SandboxGroup = mockSandboxGroup(setOf(TestClass::class.java))
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(mock(), sandboxGroup)

        val serializer = builder
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .build()

        val bytes = serializer.serialize(TestClass(TEST_INT, TEST_STRING))
        val deserialized = serializer.deserialize(bytes, TestClass::class.java)

        assertThat(deserialized.someInt).isEqualTo(TEST_INT)
        assertThat(deserialized.someString).isEqualTo(TEST_STRING)
    }

    @Test
    fun `registering the same singleton token twice`() {
        class Tester(val someInt: Int) : SingletonSerializeAsToken
        val instance = Tester(1)
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(
            mock(),
            mockSandboxGroup(setOf(Tester::class.java))
        )
        val serializer = builder
            .addSerializer(TestClass::class.java, TestClass.Serializer())
            .addSingletonSerializableInstances(setOf(instance))
            .addSingletonSerializableInstances(setOf(instance))
            .build()

        val bytes = serializer.serialize(instance)
        val tested = serializer.deserialize(bytes, instance::class.java)

        assertThat(tested).isSameAs(instance)
    }

    @ParameterizedTest
    @ValueSource(classes = [
        PublicKey::class, BCEdDSAPublicKey::class, CompositeKey::class,
        BCECPublicKey::class, BCRSAPublicKey::class, BCSphincs256PublicKey::class
    ])
    fun `serializers of public keys cannot be added`(type: Class<*>) {
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(
            mock(), mockSandboxGroup(emptySet())
        )

        assertThatExceptionOfType(CordaKryoException::class.java).isThrownBy {
            builder.addSerializer(type, mock())
        }.withMessage("Custom serializers for public keys are not allowed")
    }

    @ParameterizedTest
    @ValueSource(classes = [
        PrivateKey::class, BCEdDSAPrivateKey::class, BCECPrivateKey::class,
        BCRSAPrivateCrtKey::class, BCSphincs256PrivateKey::class
    ])
    fun `serializers of private keys cannot be added`(type: Class<*>) {
        val builder: CheckpointSerializerBuilder = KryoCheckpointSerializerBuilderImpl(
            mock(), mockSandboxGroup(emptySet())
        )

        assertThatExceptionOfType(CordaKryoException::class.java).isThrownBy {
            builder.addSerializer(type, mock())
        }.withMessage("Custom serializers for private keys are not allowed")
    }
}
