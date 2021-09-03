package net.corda.common.identity

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.AttributeTypeAndValue
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import java.util.Locale
import javax.security.auth.x500.X500Principal

/**
 * X.500 distinguished name data type customised to how Corda uses names. This restricts the attributes to those Corda
 * supports, and requires that organisation, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions
 *
 * @property commonName optional name by the which the entity is usually known. Used only for services (for
 * organisations, the [organisation] property is the name). Corresponds to the "CN" attribute type.
 * @property organisationUnit optional name of a unit within the [organisation]. Corresponds to the "OU" attribute type.
 * @property organisation name of the organisation. Corresponds to the "O" attribute type.
 * @property locality locality of the organisation, typically nearest major city. For distributed services this would be
 * where one of the organisations is based. Corresponds to the "L" attribute type.
 * @property state the full name of the state or province the organisation is based in. Corresponds to the "ST"
 * attribute type.
 * @property country country the organisation is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
 * attribute type.
 */
data class CordaX500Name(
    val commonName: String?,
    val organisationUnit: String?,
    val organisation: String,
    val locality: String,
    val state: String?,
    val country: String
) {
    constructor(commonName: String, organisation: String, locality: String, country: String) :
            this(
                commonName = commonName,
                organisationUnit = null,
                organisation = organisation,
                locality = locality,
                state = null,
                country = country
            )

    /**
     * @param organisation name of the organisation.
     * @param locality locality of the organisation, typically nearest major city.
     * @param country country the organisation is in, as an ISO 3166-1 2-letter country code.
     */
    constructor(organisation: String, locality: String, country: String) : this(null, null, organisation, locality, null, country)

    init {
        // Legal name checks.
        LegalNameValidator.validateOrganization(organisation)

        require(country in countryCodes) { "Invalid country code $country" }

        require(organisation.length < MAX_LENGTH_ORGANISATION) {
            "Organisation attribute (O) must contain less then $MAX_LENGTH_ORGANISATION characters."
        }
        require(locality.length < MAX_LENGTH_LOCALITY) { "Locality attribute (L) must contain less then $MAX_LENGTH_LOCALITY characters." }

        state?.let { require(it.length < MAX_LENGTH_STATE) { "State attribute (ST) must contain less then $MAX_LENGTH_STATE characters." } }
        organisationUnit?.let {
            require(it.length < MAX_LENGTH_ORGANISATION_UNIT) {
                "Organisation Unit attribute (OU) must contain less then $MAX_LENGTH_ORGANISATION_UNIT characters."
            }
        }
        commonName?.let {
            require(it.length < MAX_LENGTH_COMMON_NAME) {
                "Common Name attribute (CN) must contain less then $MAX_LENGTH_COMMON_NAME characters."
            }
        }
    }

    companion object {
        const val MAX_LENGTH_ORGANISATION = 128
        const val MAX_LENGTH_LOCALITY = 64
        const val MAX_LENGTH_STATE = 64
        const val MAX_LENGTH_ORGANISATION_UNIT = 64
        const val MAX_LENGTH_COMMON_NAME = 64

        private val supportedAttributes = setOf(BCStyle.O, BCStyle.C, BCStyle.L, BCStyle.CN, BCStyle.ST, BCStyle.OU)
        private const val unspecifiedCountry = "ZZ"
        @Suppress("SpreadOperator")
        private val countryCodes: Set<String> = setOf(*Locale.getISOCountries(), unspecifiedCountry)

        @JvmStatic
        fun build(principal: X500Principal): CordaX500Name {
            val attrsMap = principal.toAttributesMap(supportedAttributes)
            val CN = attrsMap[BCStyle.CN]
            val OU = attrsMap[BCStyle.OU]
            val O = requireNotNull(attrsMap[BCStyle.O]) { "Corda X.500 names must include an O attribute" }
            val L = requireNotNull(attrsMap[BCStyle.L]) { "Corda X.500 names must include an L attribute" }
            val ST = attrsMap[BCStyle.ST]
            val C = requireNotNull(attrsMap[BCStyle.C]) { "Corda X.500 names must include an C attribute" }
            return CordaX500Name(CN, OU, O, L, ST, C)
        }

        @JvmStatic
        fun parse(name: String): CordaX500Name = build(X500Principal(name))
    }

    @Transient
    private var _x500Principal: X500Principal? = null

    /** Return the [X500Principal] equivalent of this name. */
    val x500Principal: X500Principal
        get() {
            return _x500Principal ?: X500Principal(this.toX500Name().encoded).also { _x500Principal = it }
        }

    override fun toString(): String = x500Principal.toString()

    /**
     * Return the underlying X.500 name from this Corda-safe X.500 name. These are guaranteed to have a consistent
     * ordering, such that their `toString()` function returns the same value every time for the same [CordaX500Name].
     */
    private fun toX500Name(): X500Name {
        return X500NameBuilder(BCStyle.INSTANCE).apply {
            addRDN(BCStyle.C, country)
            state?.let { addRDN(BCStyle.ST, it) }
            addRDN(BCStyle.L, locality)
            addRDN(BCStyle.O, organisation)
            organisationUnit?.let { addRDN(BCStyle.OU, it) }
            commonName?.let { addRDN(BCStyle.CN, it) }
        }.build()
    }
}

/**
 * Transforms the X500Principal to the attributes map.
 *
 * @param supportedAttributes list of supported attributes. If empty, it accepts all the attributes.
 *
 * @return attributes map for this principal
 * @throws IllegalArgumentException if this principal consists of duplicated attributes or the attribute is not supported.
 *
 */
private fun X500Principal.toAttributesMap(supportedAttributes: Set<ASN1ObjectIdentifier> = emptySet()): Map<ASN1ObjectIdentifier, String> {
    val x500Name = X500Name.getInstance(this.encoded)
    val attrsMap: Map<ASN1ObjectIdentifier, String> = x500Name.rdNs
        .flatMap { it.typesAndValues.asList() }
        .groupBy(AttributeTypeAndValue::getType, AttributeTypeAndValue::getValue)
        .mapValues {
            require(it.value.size == 1) { "Duplicate attribute ${it.key}" }
            it.value[0].toString()
        }
    if (supportedAttributes.isNotEmpty()) {
        (attrsMap.keys - supportedAttributes).let { unsupported ->
            require(unsupported.isEmpty()) {
                "The following attribute${if (unsupported.size > 1) "s are" else " is"} not supported in Corda: " +
                        unsupported.map { BCStyle.INSTANCE.oidToDisplayName(it) }
            }
        }
    }
    return attrsMap
}
