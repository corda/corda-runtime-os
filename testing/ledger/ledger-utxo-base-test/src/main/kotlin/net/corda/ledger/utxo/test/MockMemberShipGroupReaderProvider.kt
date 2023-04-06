package net.corda.ledger.utxo.test

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.v5.crypto.SignatureSpec
import org.mockito.Mockito
import org.mockito.kotlin.any

internal fun mockMembershipGroupReaderProvider(): MembershipGroupReaderProvider {

    val mockMembershipGroupReaderProvider = Mockito.mock(MembershipGroupReaderProvider::class.java)
    val mockGroupReader = Mockito.mock(MembershipGroupReader::class.java)
    val mockSignedGroupParameters = Mockito.mock(SignedGroupParameters::class.java)

    Mockito.`when`(mockSignedGroupParameters.bytes).thenReturn(byteArrayOf(1, 1, 11))
    Mockito.`when`(mockSignedGroupParameters.hash).thenReturn(SecureHashImpl("algo", byteArrayOf(1, 2, 11)))
    Mockito.`when`(mockSignedGroupParameters.signature).thenReturn(Mockito.mock(DigitalSignatureWithKey::class.java))
    Mockito.`when`(mockSignedGroupParameters.signatureSpec).thenReturn(Mockito.mock(SignatureSpec::class.java))
    Mockito.`when`(mockGroupReader.signedGroupParameters).thenReturn(mockSignedGroupParameters)
    Mockito.`when`(mockMembershipGroupReaderProvider.getGroupReader(any())).thenReturn(mockGroupReader)

    return mockMembershipGroupReaderProvider
}