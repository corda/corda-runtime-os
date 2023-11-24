package net.corda.membership.lib

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.KeyValuePair
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_BACKCHAIN_REQUIRED
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_KEYS_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_NAME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY
import net.corda.membership.lib.exceptions.InvalidGroupParametersUpdateException
import net.corda.membership.lib.notary.MemberNotaryDetails
import net.corda.membership.lib.notary.MemberNotaryKey
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.security.PublicKey
import java.time.Instant
import org.junit.jupiter.api.assertThrows

class GroupParametersNotaryUpdaterTest {
    private companion object {
        const val EPOCH = 1
        const val NOTARY_SERVICE_A = "O=NotaryA, L=LDN, C=GB"
        const val NOTARY_SERVICE_B = "O=NotaryB, L=LDN, C=GB"
        const val NOTARY_SERVICE_C = "O=NotaryC, L=LDN, C=GB"
        const val NOTARY_PROTOCOL_A = "net.corda.notary.MyNotaryServiceA"
        const val NOTARY_PROTOCOL_B = "net.corda.notary.MyNotaryServiceB"
        const val NOTARY_PROTOCOL_C = "net.corda.notary.MyNotaryServiceC"
        const val NOTARY_KEY_A = "test-key-a"
        const val NOTARY_KEY_B = "test-key-b"
        const val NOTARY_KEY_C = "test-key-c"
    }
    private val notaryServices = listOf(NOTARY_SERVICE_A, NOTARY_SERVICE_B, NOTARY_SERVICE_C)
    private val notaryProtocols = listOf(NOTARY_PROTOCOL_A, NOTARY_PROTOCOL_B, NOTARY_PROTOCOL_C)
    private val notaryKeys = listOf(NOTARY_KEY_A, NOTARY_KEY_B, NOTARY_KEY_C)
    private val notaryAx500Name = MemberX500Name.parse(NOTARY_SERVICE_A)
    private val notaryBx500Name = MemberX500Name.parse(NOTARY_SERVICE_B)
    private val notaryCx500Name = MemberX500Name.parse(NOTARY_SERVICE_C)
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn "test-key"
    }
    private val clock = TestClock(Instant.ofEpochMilli(10))
    private val notaryUpdater = GroupParametersNotaryUpdater(keyEncodingService, clock)
    private val originalGroupParameters = mapOf(
        EPOCH_KEY to EPOCH.toString(),
        MODIFIED_TIME_KEY to Instant.ofEpochMilli(1).toString()
    )

    @Test
    fun `can add new notary service to group parameters`() {
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, setOf(1, 3), listOf(notaryKey), true)
        val (epoch, updatedGroupParameters) = notaryUpdater.addNewNotaryService(originalGroupParameters, notaryDetails)

        verify(keyEncodingService).encodeAsString(publicKey)
        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(updatedGroupParameters.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), "test-key"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), NOTARY_SERVICE_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), NOTARY_PROTOCOL_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1), "3"),
            KeyValuePair(String.format(NOTARY_SERVICE_BACKCHAIN_REQUIRED,  0), true.toString())
        )
    }

    @Test
    fun `notary protocol must be specified to add new notary service`() {
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryToAdd = MemberNotaryDetails(notaryAx500Name, null, setOf(1, 3), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> {
            notaryUpdater.addNewNotaryService(originalGroupParameters, notaryToAdd)
        }
        assertThat(ex).hasMessageContaining("protocol must be specified")
    }

    @Test
    fun `exception is thrown when adding new notary if notary protocol is specified but versions are missing`() {
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryToAdd = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, emptySet(), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> {
            notaryUpdater.addNewNotaryService(originalGroupParameters, notaryToAdd)
        }
        assertThat(ex).hasMessageContaining("protocol versions are missing")
    }

    @Test
    fun `can add a notary to an existing notary service`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0) to NOTARY_KEY_A,
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1) to "3",
        )
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, setOf(1, 3), listOf(notaryKey), true)

        val (epoch, updatedGroupParameters) = notaryUpdater.updateExistingNotaryService(
            currentGroupParameters,
            notaryDetails,
            5,
            setOf(1, 3)
        )

        verify(keyEncodingService).encodeAsString(publicKey)
        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(updatedGroupParameters!!.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), NOTARY_KEY_A),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 1), "test-key"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), NOTARY_SERVICE_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), NOTARY_PROTOCOL_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "3"),
        )
    }

    @Test
    fun `adding a notary to existing notary service sets protocol versions to intersection of versions of individual notary vnodes`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0) to NOTARY_KEY_A,
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1) to "3",
        )
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, setOf(1, 3, 4), listOf(notaryKey), true)

        val (epoch, updatedGroupParameters) = notaryUpdater.updateExistingNotaryService(
            currentGroupParameters,
            notaryDetails,
            5,
            setOf(1, 3)
        )

        verify(keyEncodingService).encodeAsString(publicKey)
        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(updatedGroupParameters!!.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), NOTARY_KEY_A),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 1), "test-key"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), NOTARY_SERVICE_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), NOTARY_PROTOCOL_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "3"),
        )
    }

    @Test
    fun `notary protocol must match that of existing notary service`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0) to NOTARY_KEY_A,
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1) to "3",
        )
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, "incorrect.plugin.type", emptySet(), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> { notaryUpdater.updateExistingNotaryService(
            currentGroupParameters,
            notaryDetails,
            5,
            setOf(1, 3)
        )}
        assertThat(ex).hasMessageContaining("protocols do not match")
    }

    @Test
    fun `exception is thrown when updating notary service if notary protocol is specified but versions are missing`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0) to NOTARY_KEY_A,
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1) to "3",
        )
        val publicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(publicKey, mock(), mock())
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, emptySet(), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> { notaryUpdater.updateExistingNotaryService(
            currentGroupParameters,
            notaryDetails,
            5,
            setOf(1, 3)
        )}
        assertThat(ex).hasMessageContaining("versions are missing")
    }

    @Test
    fun `if no keys are in the notary details service then the updated group parameters are null`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0) to NOTARY_KEY_A,
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1) to "3",
        )
        val notaryDetails = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, setOf(1, 2, 3), emptyList(), true)

        val (epoch, updatedGroupParameters) = notaryUpdater.updateExistingNotaryService(
            currentGroupParameters,
            notaryDetails,
            5,
            setOf(1, 3)
        )
        assertThat(epoch).isNull()
        assertThat(updatedGroupParameters).isNull()
    }

    @Test
    fun `can remove notary service from group parameters`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0) to "test-key",
            String.format(NOTARY_SERVICE_NAME_KEY, 0) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0) to NOTARY_PROTOCOL_A,
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0) to "1",
            String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 1) to "3",
        )

        val (epoch, newGroupParameters) = notaryUpdater.removeNotaryService(currentGroupParameters, 0)

        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(newGroupParameters.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString())
        )
    }

    @Test
    fun `if we remove the first notary service from group parameters with three services, the services are numbered contiguously`() {
        val currentGroupParameters = originalGroupParameters.toMutableMap()
        for(notaryService in 0 until 3) {
            currentGroupParameters[String.format(NOTARY_SERVICE_KEYS_KEY, notaryService, 0)] = notaryKeys[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_NAME_KEY, notaryService)] = notaryServices[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_PROTOCOL_KEY, notaryService)] = notaryProtocols[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, notaryService, 0)] = notaryService.toString()
        }

        val (epoch, newGroupParameters) = notaryUpdater.removeNotaryService(currentGroupParameters, 0)

        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(newGroupParameters.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), notaryKeys[1]),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServices[1]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryProtocols[1]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 1, 0), notaryKeys[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 1), notaryServices[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 1), notaryProtocols[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 1, 0), "2")
        )
    }

    @Test
    fun `if we remove the second notary service from group parameters with three services, the services are numbered contiguously`() {
        val currentGroupParameters = originalGroupParameters.toMutableMap()
        for(notaryService in 0 until 3) {
            currentGroupParameters[String.format(NOTARY_SERVICE_KEYS_KEY, notaryService, 0)] = notaryKeys[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_NAME_KEY, notaryService)] = notaryServices[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_PROTOCOL_KEY, notaryService)] = notaryProtocols[notaryService]
            currentGroupParameters[String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, notaryService, 0)] = notaryService.toString()
        }

        val (epoch, newGroupParameters) = notaryUpdater.removeNotaryService(currentGroupParameters, 1)
        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(newGroupParameters.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 0, 0), notaryKeys[0]),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 0), notaryServices[0]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 0), notaryProtocols[0]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 0, 0), "0"),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 1, 0), notaryKeys[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 1), notaryServices[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 1), notaryProtocols[2]),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 1, 0), "2")
        )
    }

    @Test
    fun `can remove a notary from an existing notary service`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            "key" to "value",
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A
        )
        val notaryToRemovePublicKey = mock<PublicKey>()
        val notaryToRemoveKey = MemberNotaryKey(notaryToRemovePublicKey, mock(), mock())
        val otherPublicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(otherPublicKey, mock(), mock())
        val notaryToRemove = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, setOf(1, 3), listOf(notaryToRemoveKey), true)
        val otherNotary1 = MemberNotaryDetails(notaryBx500Name, NOTARY_PROTOCOL_A, setOf(1, 2, 3, 4, 5), listOf(notaryKey), true)
        val otherNotary2 = MemberNotaryDetails(notaryCx500Name, NOTARY_PROTOCOL_A, setOf(1, 2, 3, 6, 7), listOf(notaryKey), true)

        val (epoch, updatedGroupParameters) = notaryUpdater.removeNotaryFromExistingNotaryService(
            currentGroupParameters,
            notaryToRemove,
            5,
            listOf(otherNotary1, otherNotary2),
        )

        verify(keyEncodingService, times(2)).encodeAsString(otherPublicKey)
        verify(keyEncodingService, never()).encodeAsString(notaryToRemovePublicKey)
        assertThat(epoch).isEqualTo(EPOCH + 1)
        assertThat(updatedGroupParameters!!.items).containsExactlyInAnyOrder(
            KeyValuePair(EPOCH_KEY, (EPOCH + 1).toString()),
            KeyValuePair(MODIFIED_TIME_KEY, clock.instant().toString()),
            KeyValuePair("key", "value"),
            KeyValuePair(String.format(NOTARY_SERVICE_KEYS_KEY, 5, 0), "test-key"),
            KeyValuePair(String.format(NOTARY_SERVICE_NAME_KEY, 5), NOTARY_SERVICE_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5), NOTARY_PROTOCOL_A),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 0), "1"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 1), "2"),
            KeyValuePair(String.format(NOTARY_SERVICE_PROTOCOL_VERSIONS_KEY, 5, 2), "3"),
        )
    }

    @Test
    fun `cannot remove a notary from an existing notary service if protocols don't match`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            "key" to "value",
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A
        )
        val notaryToRemovePublicKey = mock<PublicKey>()
        val notaryToRemoveKey = MemberNotaryKey(notaryToRemovePublicKey, mock(), mock())
        val otherPublicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(otherPublicKey, mock(), mock())
        val notaryToRemove = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_B, setOf(1, 3), listOf(notaryToRemoveKey), true)
        val otherNotary1 = MemberNotaryDetails(notaryBx500Name, NOTARY_PROTOCOL_A, setOf(1), listOf(notaryKey), true)
        val otherNotary2 = MemberNotaryDetails(notaryCx500Name, NOTARY_PROTOCOL_A, setOf(1), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> { notaryUpdater.removeNotaryFromExistingNotaryService(
            currentGroupParameters,
            notaryToRemove,
            5,
            listOf(otherNotary1, otherNotary2),
        )}
        assertThat(ex).hasMessageContaining("protocols do not match")
    }

    @Test
    fun `cannot remove a notary from an existing notary service if versions are missing`() {
        val currentGroupParameters = originalGroupParameters + mapOf(
            "key" to "value",
            String.format(NOTARY_SERVICE_NAME_KEY, 5) to NOTARY_SERVICE_A,
            String.format(NOTARY_SERVICE_PROTOCOL_KEY, 5) to NOTARY_PROTOCOL_A
        )
        val notaryToRemovePublicKey = mock<PublicKey>()
        val notaryToRemoveKey = MemberNotaryKey(notaryToRemovePublicKey, mock(), mock())
        val otherPublicKey = mock<PublicKey>()
        val notaryKey = MemberNotaryKey(otherPublicKey, mock(), mock())
        val notaryToRemove = MemberNotaryDetails(notaryAx500Name, NOTARY_PROTOCOL_A, emptySet(), listOf(notaryToRemoveKey), true)
        val otherNotary1 = MemberNotaryDetails(notaryBx500Name, NOTARY_PROTOCOL_A, setOf(1), listOf(notaryKey), true)
        val otherNotary2 = MemberNotaryDetails(notaryCx500Name, NOTARY_PROTOCOL_A, setOf(1), listOf(notaryKey), true)

        val ex = assertThrows<InvalidGroupParametersUpdateException> { notaryUpdater.removeNotaryFromExistingNotaryService(
            currentGroupParameters,
            notaryToRemove,
            5,
            listOf(otherNotary1, otherNotary2),
        )}
        assertThat(ex).hasMessageContaining("versions are missing.")
    }
}