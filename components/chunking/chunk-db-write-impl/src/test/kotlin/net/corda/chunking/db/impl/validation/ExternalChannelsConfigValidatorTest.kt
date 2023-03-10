package net.corda.chunking.db.impl.validation

import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ExternalChannelsConfigValidatorTest {

    private val externalChannelsConfigValidator = ExternalChannelsConfigValidatorImpl()

    @Test
    fun `throws exception when string is not null because the method is not implemented`() {
        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(
                cpkIdentifier = CpkIdentifier(
                    "name",
                    "1.0",
                    SecureHash("SHA-256", "abc".toByteArray())
                ),
                ""
            )
        }

        assertThrows<NotImplementedError> {
            externalChannelsConfigValidator.validate(
                cpkIdentifier = CpkIdentifier(
                    "name",
                    "1.0",
                    SecureHash("SHA-256", "abc".toByteArray())
                ),
                "invalid configuration"
            )
        }
    }

    @Test
    fun `does not throw exception when string is null`() {
        assertDoesNotThrow {
            externalChannelsConfigValidator.validate(
                cpkIdentifier = CpkIdentifier(
                    "name",
                    "1.0",
                    SecureHash("SHA-256", "abc".toByteArray())
                ),
                null
            )
        }
    }
}
