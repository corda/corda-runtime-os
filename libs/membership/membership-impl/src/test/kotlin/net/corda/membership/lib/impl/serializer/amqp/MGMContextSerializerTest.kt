package net.corda.membership.lib.impl.serializer.amqp

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.impl.utils.createDummyMgmContext
import net.corda.v5.membership.MGMContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MGMContextSerializerTest {
    private companion object {
        val cipherSchemeMetadata = CipherSchemeMetadataImpl()
        val converters = listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(cipherSchemeMetadata),
            PublicKeyConverter(cipherSchemeMetadata),
        )
        val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(converters)
        val mgmContextSerializer = MGMContextSerializer(layeredPropertyMapFactory)

        val serializationServiceWithWireTx = TestSerializationService.getTestSerializationService({
            it.register(mgmContextSerializer, it)
        }, cipherSchemeMetadata)
    }

    @Test
    fun `Should serialize and then deserialize MGMContext`() {
        val mgmContext = createDummyMgmContext(converters) as MGMContext
        val serialized = serializationServiceWithWireTx.serialize(mgmContext)
        val deserialized = serializationServiceWithWireTx.deserialize(serialized, MGMContext::class.java)
        Assertions.assertThat(deserialized).isEqualTo(mgmContext)
    }
}
