package net.corda.membership.lib.impl.serializer.amqp

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.impl.converter.PublicKeyConverter
import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.impl.converter.EndpointInfoConverter
import net.corda.membership.lib.impl.converter.MemberNotaryDetailsConverter
import net.corda.membership.lib.impl.utils.createDummyMemberContext
import net.corda.v5.membership.MemberContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemberContextSerializerTest {
    private companion object {
        val cipherSchemeMetadata = CipherSchemeMetadataImpl()
        val converters = listOf(
            EndpointInfoConverter(),
            MemberNotaryDetailsConverter(cipherSchemeMetadata),
            PublicKeyConverter(cipherSchemeMetadata),
        )
        val layeredPropertyMapFactory = LayeredPropertyMapMocks.createFactory(converters)
        val memberContextSerializer = MemberContextSerializer(layeredPropertyMapFactory)

        val serializationServiceWithWireTx = TestSerializationService.getTestSerializationService({
            it.register(memberContextSerializer, it)
        }, cipherSchemeMetadata)
    }

    @Test
    fun `Should serialize and then deserialize MemberContext`() {
        val memberContext = createDummyMemberContext(converters) as MemberContext
        val serialized = serializationServiceWithWireTx.serialize(memberContext)
        val deserialized = serializationServiceWithWireTx.deserialize(serialized, MemberContext::class.java)
        assertThat(deserialized).isEqualTo(memberContext)
    }
}
