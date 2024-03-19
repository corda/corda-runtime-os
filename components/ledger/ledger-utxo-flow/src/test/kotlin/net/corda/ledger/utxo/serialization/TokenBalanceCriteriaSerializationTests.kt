package net.corda.ledger.utxo.serialization

import net.corda.crypto.core.SecureHashImpl
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializerFactoryBuilder
import net.corda.internal.serialization.amqp.currentSandboxGroup
import net.corda.internal.serialization.amqp.helper.testSerializationContext
import net.corda.internal.serialization.registerCustomSerializers
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.token.selection.Strategy.PRIORITY
import net.corda.v5.ledger.utxo.token.selection.Strategy.RANDOM
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal.TEN
import kotlin.test.assertEquals

class TokenBalanceCriteriaSerializationTests {

    companion object {
        @JvmStatic
        fun testData(): List<TokenClaimCriteria> {
            val tokenType = "tokenType"
            val secureHash = SecureHashImpl("SHA-256", ByteArray(16))
            val memberX500Name = MemberX500Name("organization", "locality", "GB")
            val symbol = "symbol"
            return listOf(
                TokenClaimCriteria(tokenType, secureHash, memberX500Name, symbol, TEN),
                TokenClaimCriteria(tokenType, secureHash, memberX500Name, symbol, TEN, null),
                TokenClaimCriteria(tokenType, secureHash, memberX500Name, symbol, TEN, RANDOM),
                TokenClaimCriteria(tokenType, secureHash, memberX500Name, symbol, TEN, PRIORITY)
            )
        }
    }

    private val serializerFactory = SerializerFactoryBuilder
        .build(testSerializationContext.currentSandboxGroup())
        .also { registerCustomSerializers(it) }

    @ParameterizedTest
    @MethodSource("testData")
    fun testSerializationTestsSerialization(instance: TokenClaimCriteria) {
        // Serialize
        val bytes = SerializationOutput(serializerFactory).serialize(instance, testSerializationContext)

        // Deserialize
        val deserialized = DeserializationInput(serializerFactory).deserialize(bytes, testSerializationContext)

        assertEquals(instance, deserialized)
    }
}
