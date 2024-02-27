package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.data.KeyValuePairList
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HandlerUtilsTest {

    @Nested
    inner class GroupParametersEntityToAvro {

        @Test
        fun `unsigned params are returned successfully`() {
            val params = "params".toByteArray()
            val result = assertDoesNotThrow {
                GroupParametersEntity(
                    1,
                    params,
                    null,
                    null,
                    null
                ).toAvro()
            }

            assertThat(result.groupParameters.array()).isEqualTo(params)
            assertThat(result.mgmSignatureSpec).isNull()
            assertThat(result.mgmSignature).isNull()
        }

        @Test
        fun `partially signed params are returned successfully (no key)`() {
            val params = "params".toByteArray()
            val result = assertDoesNotThrow {
                GroupParametersEntity(
                    1,
                    params,
                    null,
                    "bytes".toByteArray(),
                    "spec"
                ).toAvro()
            }

            assertThat(result.groupParameters.array()).isEqualTo(params)
            assertThat(result.mgmSignatureSpec).isNull()
            assertThat(result.mgmSignature).isNull()
        }

        @Test
        fun `partially signed params are returned successfully (no signature content)`() {
            val params = "params".toByteArray()
            val result = assertDoesNotThrow {
                GroupParametersEntity(
                    1,
                    params,
                    "bytes".toByteArray(),
                    null,
                    "spec"
                ).toAvro()
            }

            assertThat(result.groupParameters.array()).isEqualTo(params)
            assertThat(result.mgmSignatureSpec).isNull()
            assertThat(result.mgmSignature).isNull()
        }

        @Test
        fun `partially signed params are returned successfully (no spec)`() {
            val params = "params".toByteArray()
            val result = assertDoesNotThrow {
                GroupParametersEntity(
                    1,
                    params,
                    "bytes".toByteArray(),
                    "bytes".toByteArray(),
                    null,
                ).toAvro()
            }

            assertThat(result.groupParameters.array()).isEqualTo(params)
            assertThat(result.mgmSignatureSpec).isNull()
            assertThat(result.mgmSignature).isNull()
        }

        @Test
        fun `signed params are returned successfully`() {
            val sigBytes = "sig".toByteArray()
            val sigPubKey = "pub-key".toByteArray()
            val params = "params".toByteArray()
            val result = assertDoesNotThrow {
                GroupParametersEntity(
                    1,
                    params,
                    sigPubKey,
                    sigBytes,
                    "spec"
                ).toAvro()
            }

            assertThat(result.groupParameters.array()).isEqualTo(params)
            assertThat(result.mgmSignatureSpec.signatureName).isEqualTo("spec")
            assertThat(result.mgmSignatureSpec.params).isNull()
            assertThat(result.mgmSignatureSpec.customDigestName).isNull()
            assertThat(result.mgmSignature.bytes.array()).isEqualTo(sigBytes)
            assertThat(result.mgmSignature.publicKey.array()).isEqualTo(sigPubKey)
        }
    }

    @Nested
    inner class DeserialiseKeyValuePairList {
        private val deserializer: CordaAvroDeserializer<KeyValuePairList> = mock()

        @Test
        fun `can return successfully`() {
            whenever(deserializer.deserialize(any())).doReturn(KeyValuePairList(emptyList()))
            val result = assertDoesNotThrow { deserializer.deserializeKeyValuePairList("test".toByteArray()) }
            assertThat(result).isInstanceOf(KeyValuePairList::class.java)
        }

        @Test
        fun `throws persistence exception if error occurs`() {
            whenever(deserializer.deserialize(any())).doReturn(null)
            assertThrows<MembershipPersistenceException> {
                deserializer.deserializeKeyValuePairList("test".toByteArray())
            }.also {
                assertThat(it.message).contains("Failed to deserialize")
            }
        }
    }
}
