package net.corda.membership.lib.impl

import net.corda.membership.lib.SignedGroupParameters
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.membership.GroupParameters

class SignedGroupParametersImpl(
    private val groupParameters: GroupParameters,
    override val bytes: ByteArray,
    override val signature: DigitalSignature.WithKey
) : GroupParameters by groupParameters, SignedGroupParameters
