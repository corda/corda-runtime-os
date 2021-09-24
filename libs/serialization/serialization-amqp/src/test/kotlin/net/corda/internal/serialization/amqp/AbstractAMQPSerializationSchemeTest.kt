package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.AbstractAMQPSerializationScheme
import net.corda.internal.serialization.CordaSerializationMagic
import net.corda.internal.serialization.AllWhitelist
import net.corda.internal.serialization.SerializationContextImpl
import net.corda.internal.serialization.SerializationFactoryCacheKey
import net.corda.internal.serialization.amqp.testutils.serializationProperties
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.utilities.toSynchronised
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.serialization.SerializationContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.ThreadLocalRandom

class AbstractAMQPSerializationSchemeTest {

    @Test
    fun `number of cached factories must be bounded by maxFactories`() {
        val genesisContext = SerializationContextImpl(
            ByteSequence.of(byteArrayOf('c'.toByte(), 'o'.toByte(), 'r'.toByte(), 'd'.toByte(), 'a'.toByte(), 0.toByte(),
                0.toByte(), 1.toByte())),
            ClassLoader.getSystemClassLoader(),
            AllWhitelist,
            serializationProperties,
            false,
            SerializationContext.UseCase.RPCClient,
            null)

        val maxFactories = 512
        val backingMap = AccessOrderLinkedHashMap<SerializationFactoryCacheKey, SerializerFactory> { maxFactories }.toSynchronised()
        val scheme = object : AbstractAMQPSerializationScheme(mock(CipherSchemeMetadata::class.java)) {

            override fun canDeserializeVersion(
                magic: CordaSerializationMagic, target: SerializationContext.UseCase
            ): Boolean {
                return true
            }
        }

        val testString = "TEST${ThreadLocalRandom.current().nextInt()}"
        val serialized = scheme.serialize(testString, genesisContext)
        val factory = testDefaultFactory()
        val deserialized = DeserializationInput(factory).deserialize(serialized, genesisContext)

        assertThat(testString).isEqualTo(deserialized)
        assertThat(backingMap.size).isLessThanOrEqualTo(maxFactories)
    }
}