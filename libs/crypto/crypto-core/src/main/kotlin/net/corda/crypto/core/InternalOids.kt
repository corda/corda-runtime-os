package net.corda.crypto.core

import net.corda.v5.crypto.CordaOID.OID_COMPOSITE_KEY
import net.corda.v5.crypto.CordaOID.OID_COMPOSITE_SIGNATURE
import org.bouncycastle.asn1.ASN1ObjectIdentifier

/** ASN1ObjectIdentifier for CompositeKey. */
val OID_COMPOSITE_KEY_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_KEY)


/** ASN1ObjectIdentifier for CompositeSignature. */
val OID_COMPOSITE_SIGNATURE_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_SIGNATURE)
