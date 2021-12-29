package net.corda.v5.ledger.transactions

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SignedTransactionDigestTest {

    companion object {
        private val jsonMapper: ObjectMapper = ObjectMapper().apply { registerModule(KotlinModule.Builder().build()) }
    }

    @Test
    fun testToJsonString() {
        val instance = SignedTransactionDigest("txId1", listOf("firstState", "secondState"), listOf("firstSignature", "secondSignature"))
        val jsonString = instance.toJsonString()
        val newInstance = jsonMapper.readValue(jsonString, SignedTransactionDigest::class.java)
        assertThat(newInstance).isEqualTo(instance)
    }
}