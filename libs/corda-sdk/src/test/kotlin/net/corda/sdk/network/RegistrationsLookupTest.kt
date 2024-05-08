package net.corda.sdk.network

import net.corda.crypto.core.ShortHash
import net.corda.membership.rest.v1.MemberRegistrationRestResource
import net.corda.rest.client.RestClient
import net.corda.rest.client.RestConnection
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RegistrationsLookupTest {

    @Test
    fun testIsVnodeRegistrationApprovedEmptyList() {
        val mockedMemberRegistrations = mock<MemberRegistrationRestResource> {
            on { checkRegistrationProgress(any()) } doReturn emptyList()
        }
        val restConnection = mock<RestConnection<MemberRegistrationRestResource>> { on { proxy } doReturn mockedMemberRegistrations }
        val mockedRestClient = mock<RestClient<MemberRegistrationRestResource>> { on { start() } doReturn restConnection }
        val result = RegistrationsLookup().isVnodeRegistrationApproved(
            restClient = mockedRestClient,
            ShortHash.parse("123456789123")
        )
        Assertions.assertThat(result).isFalse
    }
}
