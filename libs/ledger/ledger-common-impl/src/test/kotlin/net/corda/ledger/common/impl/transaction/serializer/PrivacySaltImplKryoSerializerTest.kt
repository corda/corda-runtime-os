package net.corda.ledger.common.impl.transaction.serializer

import net.corda.kryoserialization.testkit.KryoTestUtils
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrivacySaltImplKryoSerializerTest {
    @Test
    fun `serialization of a PrivacySalt`() {
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())

        val serializer = KryoTestUtils.createCheckpointSerializer(
            mapOf(
                PrivacySaltImpl::class.java to PrivacySaltImplKryoSerializer()
            )
        )

        val bytes = serializer.serialize(privacySalt)
        val deserialized = serializer.deserialize(bytes, PrivacySaltImpl::class.java)

        assertThat(deserialized).isEqualTo(privacySalt)
    }
}
