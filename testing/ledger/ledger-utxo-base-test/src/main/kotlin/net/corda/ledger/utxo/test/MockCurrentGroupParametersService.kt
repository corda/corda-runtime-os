package net.corda.ledger.utxo.test

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.utxo.flow.impl.groupparameters.GroupParametersServiceInternal
import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.crypto.SignatureSpec
import org.mockito.Mockito

fun mockGroupParametersService(): GroupParametersServiceInternal {

    val mockGroupParametersService = Mockito.mock(GroupParametersServiceInternal::class.java)
    val mockSignedGroupParameters = Mockito.mock(SignedGroupParameters::class.java)

    Mockito.`when`(mockSignedGroupParameters.groupParameters).thenReturn(byteArrayOf(1, 1, 11))
    Mockito.`when`(mockSignedGroupParameters.hash).thenReturn(SecureHashImpl("algo", byteArrayOf(1, 2, 11)))
    Mockito.`when`(mockSignedGroupParameters.mgmSignature).thenReturn(Mockito.mock(DigitalSignatureWithKey::class.java))
    Mockito.`when`(mockSignedGroupParameters.mgmSignatureSpec).thenReturn(Mockito.mock(SignatureSpec::class.java))
    Mockito.`when`(mockGroupParametersService.currentGroupParameters).thenReturn(mockSignedGroupParameters)

    return mockGroupParametersService
}