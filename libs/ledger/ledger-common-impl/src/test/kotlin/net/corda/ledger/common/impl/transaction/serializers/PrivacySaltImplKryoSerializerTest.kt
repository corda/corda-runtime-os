package net.corda.ledger.common.impl.transaction.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PrivacySaltImplKryoSerializerTest {
    @Test
    fun `serialization of a PrivacySalt`() {
        val privacySalt = PrivacySaltImpl("1".repeat(32).toByteArray())

        val wireTransactionKryoSerializer = PrivacySaltImplKryoSerializer()

        val kryo = Kryo()
        val output = Output(100)
        wireTransactionKryoSerializer.write(kryo, output, privacySalt)
        val deserialized = wireTransactionKryoSerializer.read(kryo, Input(output.buffer), PrivacySaltImpl::class.java)

        assertThat(deserialized).isEqualTo(privacySalt)
    }
}