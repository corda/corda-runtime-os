package net.corda.flow.application.versioning.impl

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable

/**
 * [AgreedVersionAndPayload] is sent to peers during the versioning protocol to specify the agreed flow version to use along with the
 * initial payload sent from the initiator to receiving sessions.
 *
 * @property agreedVersion The [AgreedVersion] to use.
 * @property serializedPayload A nullable [ByteArray] containing the initial payload sent over a [FlowSession]. `null` if no payload was
 * sent.
 */
@CordaSerializable
data class AgreedVersionAndPayload(
    val agreedVersion: AgreedVersion,
    val serializedPayload: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AgreedVersionAndPayload

        if (agreedVersion != other.agreedVersion) return false
        if (!serializedPayload.contentEquals(other.serializedPayload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = agreedVersion.hashCode()
        result = 31 * result + serializedPayload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "AgreedVersionsAndPayload(agreedVersions=$agreedVersion, serializedPayload=${serializedPayload.contentToString()})"
    }


}