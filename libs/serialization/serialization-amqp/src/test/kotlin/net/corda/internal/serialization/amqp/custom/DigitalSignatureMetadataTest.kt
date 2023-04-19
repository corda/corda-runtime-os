package net.corda.internal.serialization.amqp.custom

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.internal.serialization.amqp.ReusableSerialiseDeserializeAssert
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.testutils.serialize
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals

class DigitalSignatureMetadataTest {
    companion object {
        const val MAP_CAPACITY = 2
        const val MAP_LOAD_FACTOR = 3.00f

        const val ADD = 0
        const val REMOVE = 1
        const val SKIP = 2
    }

    @Test
    fun `DigitalSignatureMetadata with properties of different order serializes deterministically`() {
        // We are creating below 2 maps with 2 buckets each to easily reproduce equal maps i.e.
        // same elements but different iteration through them and thus different serializations.

        val digitalSignatureProperties1 = java.util.HashMap<String, String>(MAP_CAPACITY, MAP_LOAD_FACTOR)
        // Iterating through digitalSignatureProperties1 keys looks like:
        // 0, 2, 4, 1, 3, 5
        digitalSignatureProperties1["0"] = "v0"
        digitalSignatureProperties1["1"] = "v1"
        digitalSignatureProperties1["2"] = "v2"
        digitalSignatureProperties1["3"] = "v3"
        digitalSignatureProperties1["4"] = "v4"
        digitalSignatureProperties1["5"] = "v5"

        val digitalSignatureProperties2 = java.util.HashMap<String, String>(MAP_CAPACITY, MAP_LOAD_FACTOR)
        // Iterating through digitalSignatureProperties2 keys looks like:
        // 2, 4, 0, 5, 1, 3
        digitalSignatureProperties2["5"] = "v5"
        digitalSignatureProperties2["1"] = "v1"
        digitalSignatureProperties2["2"] = "v2"
        digitalSignatureProperties2["3"] = "v3"
        digitalSignatureProperties2["4"] = "v4"
        digitalSignatureProperties2["0"] = "v0"

        // The two maps are equal
        assertEquals(digitalSignatureProperties1, digitalSignatureProperties2)

        serDesDigitalSignatureMetadata(digitalSignatureProperties1, digitalSignatureProperties2)
    }

    @Test
    fun `DigitalSignatureMetadata properties round trip serializes deterministically`() {
        val digitalSignatureProperties1 = hashMapOf<String, String>()
        for (i in 0 until 1000) {
            digitalSignatureProperties1["$i"] = "$i"
        }

        val digitalSignatureProperties2 = hashMapOf<String, String>()
        val addedEntriesKeys = arrayListOf<String>()
        val removedEntriesKeys = arrayListOf<String>()
        val skippedEntriesKeys = arrayListOf<String>()
        for (i in 0 until 1000) {
            when (Random.nextInt(3)) {
                ADD -> {
                    digitalSignatureProperties2["$i"] = "$i"
                    addedEntriesKeys.add("$i")
                }

                REMOVE -> {
                    skippedEntriesKeys.add("$i")
                    if (addedEntriesKeys.size > 0) {
                        val removedEntry = addedEntriesKeys.removeAt(Random.nextInt(addedEntriesKeys.size))
                        digitalSignatureProperties2.remove(removedEntry)
                            ?: throw IllegalStateException("Should be able to find key")
                        removedEntriesKeys.add(removedEntry)
                    }
                }

                SKIP -> {
                    skippedEntriesKeys.add("$i")
                }
            }
        }

        while (removedEntriesKeys.isNotEmpty()) {
            val removedEntry = removedEntriesKeys.removeAt(Random.nextInt(removedEntriesKeys.size))
            digitalSignatureProperties2[removedEntry] = removedEntry
        }

        while (skippedEntriesKeys.isNotEmpty()) {
            val skippedEntry = skippedEntriesKeys.removeAt(Random.nextInt(skippedEntriesKeys.size))
            digitalSignatureProperties2[skippedEntry] = skippedEntry
        }

        // The two maps are equal
        assertEquals(digitalSignatureProperties1, digitalSignatureProperties2)

        serDesDigitalSignatureMetadata(digitalSignatureProperties1, digitalSignatureProperties2)
    }
}

private fun serDesDigitalSignatureMetadata(
    digitalSignatureProperties1: HashMap<String, String>,
    digitalSignatureProperties2: HashMap<String, String>
) {
    val instant = Instant.now()
    val signatureSpec = SignatureSpecs.RSA_SHA256

    val digitalSignatureMetadata1 =
        DigitalSignatureMetadata(
            instant,
            signatureSpec,
            digitalSignatureProperties1
        )

    val digitalSignatureMetadata2 =
        DigitalSignatureMetadata(
            instant,
            signatureSpec,
            digitalSignatureProperties2
        )

    // The two maps serializations are different due to the different order their values are iterated through
    val bytes1 =
        SerializationOutput(ReusableSerialiseDeserializeAssert.factory)
            .serialize(digitalSignatureMetadata1)

    val bytes2 =
        SerializationOutput(ReusableSerialiseDeserializeAssert.factory)
            .serialize(digitalSignatureMetadata2)

    // With `DigitalSignatureMetadataSerializer` we make their serializations equal by
    // converting DigitalSignatureMetadata.properties into a sorted map first and then serialize that instead.
    assertEquals(bytes1, bytes2)
    ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(digitalSignatureMetadata1)
    ReusableSerialiseDeserializeAssert.serializeDeserializeAssert(digitalSignatureMetadata2)
}