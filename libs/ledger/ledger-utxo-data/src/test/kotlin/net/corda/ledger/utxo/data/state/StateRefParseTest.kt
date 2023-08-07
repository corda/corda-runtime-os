package net.corda.ledger.utxo.data.state

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHashAlgoName
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.base.util.ByteArrays
import net.corda.v5.ledger.utxo.StateRef
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever


class StateRefParseTest {
    private companion object {
        const val DELIMITER = ":"
    }

    private val digestService = mock(DigestService::class.java)

    @Test
    fun `parse valid state ref`() {
        val value = "SHA-256D:ED87C7285E1E34BF5E46302086F76317ACE9B17AEF7BD086EE09A5ACBD17CEA4:0"

        val lastIndexOfDelimiter = value.lastIndexOf(DELIMITER)
        val subStringBeforeDelimiter = value.substring(0, lastIndexOfDelimiter)
        val digestName = parseSecureHashAlgoName(subStringBeforeDelimiter)
        val hexString = subStringBeforeDelimiter.substring(digestName.length + 1)
        val secureHash = SecureHashImpl(digestName, ByteArrays.parseAsHex(hexString))
        whenever(digestService.parseSecureHash(subStringBeforeDelimiter)).thenReturn(secureHash)
        Assertions.assertEquals(StateRef.parse(value, digestService).transactionId.toString(), subStringBeforeDelimiter)
    }

    @Test
    fun `parse malformed with zero delimiter`() {
        val value = "XXX"
        val errorMessage = Assertions.assertThrows(
            IllegalArgumentException::class.java
        ) { StateRef.parse(value, digestService) }.message
        Assertions.assertEquals(
            "Failed to parse a StateRef from the specified value. At least one delimiter ($DELIMITER) is expected in value: $value.",
            errorMessage
        )
    }

    @Test
    fun `parse malformed index`() {
        val value = ":asdf:a"
        val errorMessage = Assertions.assertThrows(
            IllegalArgumentException::class.java
        ) { StateRef.parse(value, digestService) }.message
        Assertions.assertEquals(
            "Failed to parse a StateRef from the specified value. The index is malformed: $value.",
            errorMessage
        )
    }

    @Test
    fun `parse malformed transaction id`() {
        val value = "SHA-256D:asdf:0"
        val valueBeforeDelimiter = value.substring(0, value.lastIndexOf(DELIMITER))
        val digestName = "SHA-256D"
        val hexString = valueBeforeDelimiter.substring(digestName.length + 1)
        val digestHexStringLength = 64
        whenever(digestService.parseSecureHash(valueBeforeDelimiter)).thenThrow(
            IllegalArgumentException(
                "Digest algorithm's: \"$digestName\" required hex string length: $digestHexStringLength " +
                        "is not met by hex string: \"$hexString\""
            )
        )

        val errorMessage = Assertions.assertThrows(
            IllegalArgumentException::class.java
        ) { StateRef.parse(value, digestService) }.message
        Assertions.assertEquals(
            "Failed to parse a StateRef from the specified value. The transaction ID is malformed: ${value}.",
            errorMessage
        )
    }
}