package net.corda.membership.impl.p2p.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.member.PersistMemberRegistrationState
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.p2p.SetOwnRegistrationStatus
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.schema.Schemas.Membership.Companion.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.schema.registry.deserialize
import net.corda.virtualnode.toCorda
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class SetOwnRegistrationStatusHandlerTest {
    private val payload = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
    private val status = SetOwnRegistrationStatus(
        "id",
        RegistrationStatus.DECLINED
    )
    private val avroSchemaRegistry: AvroSchemaRegistry = mock {
        on { deserialize<SetOwnRegistrationStatus>(payload) } doReturn status
    }
    private val identity = HoldingIdentity("O=Alice, L=London, C=GB", "GroupId")
    private val header = mock<AuthenticatedMessageHeader> {
        on { destination } doReturn identity
    }
    private val handler = SetOwnRegistrationStatusHandler(avroSchemaRegistry)

    @Test
    fun `invokeAuthenticatedMessage returns PersistMemberRegistrationState command`() {
        val record = handler.invokeAuthenticatedMessage(header, payload)

        assertSoftly { softly ->
            softly.assertThat(record.topic).isEqualTo(REGISTRATION_COMMAND_TOPIC)
            softly.assertThat(record.key).isEqualTo("id-DECLINED-${identity.toCorda().shortHash}")
            softly.assertThat(record.value).isEqualTo(
                RegistrationCommand(
                    PersistMemberRegistrationState(
                        identity,
                        status
                    )
                )
            )
        }
    }
}
