package net.corda.v5.base.types

import net.corda.v5.base.annotations.CordaSerializable
import java.util.Locale
import javax.naming.directory.BasicAttributes
import javax.naming.ldap.LdapName
import javax.naming.ldap.Rdn
import javax.security.auth.x500.X500Principal

/**
 * X.500 distinguished name data type customised to how Corda uses names. This restricts the attributes to those Corda
 * supports, and requires that organisation, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions.
 *
 * The class also guaranties the reliable equality comparison regardless which order the attributes are specified when
 * parsing from the string or X500principal as well outputs the attributes to string in predictable order.
 *
 * @property commonName optional name by the which the entity is usually known. Used only for services (for
 * organisations, the [organisation] property is the name). Corresponds to the "CN" attribute type.
 * @property organisationUnit optional name of a unit within the [organisation]. Corresponds to the "OU" attribute type.
 * @property organisation name of the organisation. Corresponds to the "O" attribute type.
 * @property locality locality of the organisation, typically the nearest major city. For distributed services this would be
 * where one of the organisations is based. Corresponds to the "L" attribute type.
 * @property state the full name of the state or province the organisation is based in. Corresponds to the "ST"
 * attribute type.
 * @property country country the organisation is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
 * attribute type.
*/
@Suppress("LongParameterList")
@CordaSerializable
class MemberX500Name(
    val commonName: String?,
    val organisationUnit: String?,
    val organisation: String,
    val locality: String,
    val state: String?,
    val country: String
) {
    companion object {
        const val MAX_LENGTH_ORGANISATION = 128
        const val MAX_LENGTH_LOCALITY = 64
        const val MAX_LENGTH_STATE = 64
        const val MAX_LENGTH_ORGANISATION_UNIT = 64
        const val MAX_LENGTH_COMMON_NAME = 64

        private const val ATTRIBUTE_COMMON_NAME = "CN"
        private const val ATTRIBUTE_ORGANISATION_UNIT = "OU"
        private const val ATTRIBUTE_ORGANISATION = "O"
        private const val ATTRIBUTE_LOCALITY = "L"
        private const val ATTRIBUTE_STATE = "ST"
        private const val ATTRIBUTE_COUNTRY = "C"

        private const val UNSPECIFIED_COUNTRY = "ZZ"

        private val supportedAttributes = setOf(
            ATTRIBUTE_COMMON_NAME,
            ATTRIBUTE_ORGANISATION_UNIT,
            ATTRIBUTE_ORGANISATION,
            ATTRIBUTE_LOCALITY,
            ATTRIBUTE_STATE,
            ATTRIBUTE_COUNTRY
        )

        private val countryCodes: Set<String> = Locale.getISOCountries().toSet() + UNSPECIFIED_COUNTRY

        /**
         * Creates an instance of [MemberX500Name] from specified [X500Principal]
         *
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied.
         */
        @JvmStatic
        fun build(principal: X500Principal): MemberX500Name = parse(toAttributesMap(principal))

        /**
         * Creates an instance of [MemberX500Name] by parsing the string representation of X500 name, like
         * "CN=Alice, OU=Engineering, O=R3, L=London, C=GB".
         * Constrains are the same as for [toAttributesMap] plus some additional constrains:
         * - O, L, C are required attributes
         *
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied or
         * the name is improperly specified.
         */
        @JvmStatic
        fun parse(name: String): MemberX500Name = parse(toAttributesMap(name))

        /**
         * Parses the string representation of X500 name and builds the attribute map where the key is the
         * attributes keys, like CN, O, etc.
         * Constrains:
         * - the RDNs cannot be multivalued
         * - the attributes must have single value
         * - the only supported attributes are C, ST, L, O, OU, CN
         * - attributes cannot be duplicated
         *
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied or
         * the name is improperly specified.
         */
        @JvmStatic
        fun toAttributesMap(name: String): Map<String, String> {
            // X500Principal is used to sanitise the syntax as the LdapName will let through such string as
            // "O=VALID, L=IN,VALID, C=DE, OU=VALID, CN=VALID" where the (L) have to be escaped
            return toAttributesMap(X500Principal(name))
        }

        private fun parse(attrsMap: Map<String, String>): MemberX500Name {
            val cn = attrsMap[ATTRIBUTE_COMMON_NAME]
            val ou = attrsMap[ATTRIBUTE_ORGANISATION_UNIT]
            val o = requireNotNull(attrsMap[ATTRIBUTE_ORGANISATION]) { "Member X.500 names must include an O attribute" }
            val l = requireNotNull(attrsMap[ATTRIBUTE_LOCALITY]) { "Member X.500 names must include an L attribute" }
            val st = attrsMap[ATTRIBUTE_STATE]
            val c = requireNotNull(attrsMap[ATTRIBUTE_COUNTRY]) { "Member X.500 names must include an C attribute" }
            return MemberX500Name(cn, ou, o, l, st, c)
        }

        private fun toAttributesMap(principal: X500Principal): Map<String, String> {
            val result = mutableMapOf<String, String>()
            LdapName(principal.toString()).rdns.forEach { rdn ->
                require(rdn.size() == 1) {
                    "The RDN '$rdn' must not be multi-valued."
                }
                rdn.toAttributes().all.asSequence().forEach {
                    require(it.size() == 1) {
                        "Attribute '${it.id}' have to contain only single value."
                    }
                    val value = it.get(0)
                    require(value is String) {
                        "Attribute's '${it.id}' value must be a string"
                    }
                    require(!result.containsKey(it.id)) {
                        "Duplicate attribute ${it.id}"
                    }
                    result[it.id] = value
                }
            }
            if (supportedAttributes.isNotEmpty()) {
                (result.keys - supportedAttributes).let { unsupported ->
                    require(unsupported.isEmpty()) {
                        "The following attribute${if (unsupported.size > 1) "s are" else " is"} not supported in Corda: " +
                                unsupported.map { it }
                    }
                }
            }
            return result
        }
    }

    /**
     * @param commonName optional name by the which the entity is usually known. Used only for services (for
     * organisations, the [organisation] property is the name). Corresponds to the "CN" attribute type.
     * @param organisation name of the organisation.
     * @param locality locality of the organisation, typically the nearest major city.
     * @param country country the organisation is in, as an ISO 3166-1 2-letter country code.
     */
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
    constructor(organisation: String, locality: String, country: String) :
            this(null, null, organisation, locality, null, country)

    init {
        require(country in countryCodes) { "Invalid country code $country" }

        state?.let {
            require(it.isNotBlank()) {
                "State attribute (ST) if specified then it must be not blank."
            }
            require(it.length < MAX_LENGTH_STATE) { "State attribute (ST) must contain less then $MAX_LENGTH_STATE characters." }
        }

        require(locality.isNotBlank()) {
            "Locality attribute (L) must not be blank."
        }
        require(locality.length < MAX_LENGTH_LOCALITY) { "Locality attribute (L) must contain less then $MAX_LENGTH_LOCALITY characters." }

        require(organisation.isNotBlank()) {
            "Organisation attribute (O) if specified then it must be not blank."
        }
        require(organisation.length < MAX_LENGTH_ORGANISATION) {
            "Organisation attribute (O) must contain less then $MAX_LENGTH_ORGANISATION characters."
        }

        organisationUnit?.let {
            require(it.isNotBlank()) {
                "Organisation unit attribute (OU) if specified then it must be not blank."
            }
            require(it.length < MAX_LENGTH_ORGANISATION_UNIT) {
                "Organisation Unit attribute (OU) must contain less then $MAX_LENGTH_ORGANISATION_UNIT characters."
            }
        }

        commonName?.let {
            require(it.isNotBlank()) {
                "Common name attribute (CN) must not be blank."
            }
            require(it.length < MAX_LENGTH_COMMON_NAME) {
                "Common Name attribute (CN) must contain less then $MAX_LENGTH_COMMON_NAME characters."
            }
        }
    }

    /**
     * Returns the [X500Principal] equivalent of this name where the order of RDNs is
     * C, ST, L, O, OU, CN (the printing order would be reversed)
     */
    val x500Principal: X500Principal by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val rdns = mutableListOf<Rdn>().apply {
            add(Rdn(BasicAttributes(ATTRIBUTE_COUNTRY, country)))
            state?.let {
                add(Rdn(BasicAttributes(ATTRIBUTE_STATE, it)))
            }
            add(Rdn(BasicAttributes(ATTRIBUTE_LOCALITY, locality)))
            add(Rdn(BasicAttributes(ATTRIBUTE_ORGANISATION, organisation)))
            organisationUnit?.let {
                add(Rdn(BasicAttributes(ATTRIBUTE_ORGANISATION_UNIT, it)))
            }
            commonName?.let {
                add(Rdn(BasicAttributes(ATTRIBUTE_COMMON_NAME, it)))
            }
        }
        X500Principal(LdapName(rdns).toString())
    }

    /**
     * Returns the string equivalent of this name where the order of RDNs is CN, OU, O, L, ST, C
     */
    override fun toString(): String = x500Principal.toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemberX500Name

        if (commonName != other.commonName) return false
        if (organisationUnit != other.organisationUnit) return false
        if (organisation != other.organisation) return false
        if (locality != other.locality) return false
        if (state != other.state) return false
        if (country != other.country) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commonName?.hashCode() ?: 0
        result = 31 * result + (organisationUnit?.hashCode() ?: 0)
        result = 31 * result + organisation.hashCode()
        result = 31 * result + locality.hashCode()
        result = 31 * result + (state?.hashCode() ?: 0)
        result = 31 * result + country.hashCode()
        return result
    }
}
