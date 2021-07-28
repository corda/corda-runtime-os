@file:JvmName("CordaOID")

package net.corda.v5.crypto

import org.bouncycastle.asn1.ASN1ObjectIdentifier

/**
 * OIDs used for the Corda platform. All entries MUST be defined in this file only and they MUST NOT be removed.
 * If an OID is incorrectly assigned, it should be marked deprecated and NEVER be reused again.
 */

/** Assigned to R3, see http://www.oid-info.com/cgi-bin/display?oid=1.3.6.1.4.1.50530&action=display */
const val OID_R3_ROOT = "1.3.6.1.4.1.50530"

/** OIDs issued for the Corda platform. */
const val OID_CORDA_PLATFORM = "$OID_R3_ROOT.1"

/**
 * Identifier for the X.509 certificate extension specifying the Corda role. See
 * https://r3-cev.atlassian.net/wiki/spaces/AWG/pages/156860572/Certificate+identity+type+extension for details.
 */
const val OID_X509_EXTENSION_CORDA_ROLE = "$OID_CORDA_PLATFORM.1"

/** OID for AliasPrivateKey. */
const val OID_ALIAS_PRIVATE_KEY = "$OID_CORDA_PLATFORM.2"

/** OID for CompositeKey. */
const val OID_COMPOSITE_KEY = "$OID_CORDA_PLATFORM.3"

/** OID for CompositeSignature. */
const val OID_COMPOSITE_SIGNATURE = "$OID_CORDA_PLATFORM.4"

/** ASN1ObjectIdentifier for AliasPrivateKey. */
@JvmField
val OID_ALIAS_PRIVATE_KEY_IDENTIFIER = ASN1ObjectIdentifier(OID_ALIAS_PRIVATE_KEY)

/** ASN1ObjectIdentifier for CompositeKey. */
@JvmField
val OID_COMPOSITE_KEY_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_KEY)

/** ASN1ObjectIdentifier for CompositeSignature. */
@JvmField
val OID_COMPOSITE_SIGNATURE_IDENTIFIER = ASN1ObjectIdentifier(OID_COMPOSITE_SIGNATURE)
