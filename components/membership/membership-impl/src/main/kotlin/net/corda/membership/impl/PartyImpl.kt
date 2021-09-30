package net.corda.membership.impl

import net.corda.v5.application.identity.*
import net.corda.v5.base.types.OpaqueBytes
import java.security.PublicKey
import java.security.cert.X509Certificate

class PartyImpl(override val name: CordaX500Name, override val owningKey: PublicKey) : Party {
    constructor(certificate: X509Certificate)
            : this(CordaX500Name.build(certificate.subjectX500Principal), certificate.publicKey)

    override fun nameOrNull(): CordaX500Name = name
    override fun ref(bytes: OpaqueBytes): PartyAndReference = PartyAndReference(this, bytes)
    override fun anonymise(): AnonymousParty = AnonymousPartyImpl(owningKey)

    override fun toString() = name.toString()

    /** Anonymised parties do not include any detail apart from owning key, so equality is dependent solely on the key */
    override fun equals(other: Any?): Boolean = other === this || other is AbstractParty && other.owningKey == owningKey

    override fun hashCode(): Int = owningKey.hashCode()
}