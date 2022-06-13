package net.corda.ledger.consensual

import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.consensual.Party
import java.security.PublicKey
class PartyImpl(override val name: MemberX500Name, override val owningKey: PublicKey) : Party {
    /* // todo: looks useful.
    constructor(certificate: X509Certificate)
            : this(MemberX500Name.build(certificate.subjectX500Principal), certificate.publicKey)
    */
    // todo: review these. Name is not really used. Is this OK?
    override fun toString() = name.toString()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = owningKey.hashCode()
}