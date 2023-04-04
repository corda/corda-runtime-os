package net.corda.ledger.common.test

import net.corda.crypto.core.SecureHashImpl
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import org.mockito.Mockito
import org.mockito.kotlin.any

internal fun mockMembershipGroupReaderProvider(): MembershipGroupReaderProvider {

    val mockMembershipGroupReaderProvider = Mockito.mock(MembershipGroupReaderProvider::class.java)
    val mockGroupReader = Mockito.mock(MembershipGroupReader::class.java)
    val mockSignedGroupParameters = Mockito.mock(SignedGroupParameters::class.java)

    Mockito.`when`(mockSignedGroupParameters.hash).thenReturn(SecureHashImpl("algo", byteArrayOf(1, 2, 11)))
    Mockito.`when`(mockGroupReader.signedGroupParameters).thenReturn(mockSignedGroupParameters)
    Mockito.`when`(mockMembershipGroupReaderProvider.getGroupReader(any())).thenReturn(mockGroupReader)

    return mockMembershipGroupReaderProvider
}