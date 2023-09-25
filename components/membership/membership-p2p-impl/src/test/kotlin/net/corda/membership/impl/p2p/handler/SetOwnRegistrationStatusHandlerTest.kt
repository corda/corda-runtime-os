package net.corda.membership.impl.p2p.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.membership.lib.RegistrationStatusV2
import net.corda.membership.lib.SetOwnRegistrationStatusV2
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class SetOwnRegistrationStatusHandlerTest {
    private val payloadV1 = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
    private val statusV1 = SetOwnRegistrationStatus(
        "id",
        RegistrationStatus.DECLINED
    )
    private val payloadV2 = ByteBuffer.wrap(byteArrayOf(4, 5, 6))
    private val reason = "some reason"
    private val statusV2 = SetOwnRegistrationStatusV2(
        "id",
        RegistrationStatusV2.DECLINED,
        reason
    )
    private val avroSchemaRegistry: AvroSchemaRegistry = mock {
        on { getClassType(payloadV1) } doReturn SetOwnRegistrationStatus::class.java
        on { getClassType(payloadV2) } doReturn SetOwnRegistrationStatusV2::class.java
        on { deserialize<SetOwnRegistrationStatus>(payloadV1) } doReturn statusV1
        on { deserialize<SetOwnRegistrationStatusV2>(payloadV2) } doReturn statusV2
    }
    private val identity = HoldingIdentity("O=Alice, L=London, C=GB", "GroupId")
    private val header = mock<AuthenticatedMessageHeader> {
        on { destination } doReturn identity
    }
    private val handler = SetOwnRegistrationStatusHandler(avroSchemaRegistry)

    @Test
    fun `invokeAuthenticatedMessage returns PersistMemberRegistrationState command - V1 version converted to V2 successfully`() {
        val record = handler.invokeAuthenticatedMessage(header, payloadV1)
        val statusV2WithoutReason = SetOwnRegistrationStatusV2(
            "id",
            RegistrationStatusV2.DECLINED,
            null
        )

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            softly.assertThat(record.key).isEqualTo("id-DECLINED-${identity.toCorda().shortHash}")
            softly.assertThat(record.value).isEqualTo(
                RegistrationCommand(
                    PersistMemberRegistrationState(
                        identity,
                        statusV2WithoutReason
                    )
                )
            )
        }
    }

    @Test
    fun `invokeAuthenticatedMessage returns PersistMemberRegistrationState command - V2 version`() {
        val record = handler.invokeAuthenticatedMessage(header, payloadV2)

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            softly.assertThat(record.key).isEqualTo("id-DECLINED-${identity.toCorda().shortHash}")
            softly.assertThat(record.value).isEqualTo(
                RegistrationCommand(
                    PersistMemberRegistrationState(
                        identity,
                        statusV2
                    )
                )
            )
        }
    }
}
