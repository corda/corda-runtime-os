package net.corda.v5.base.types

import net.corda.v5.base.annotations.CordaSerializable
import java.util.Locale
import java.util.Objects
import javax.naming.InvalidNameException
import javax.naming.directory.BasicAttributes
import javax.naming.ldap.LdapName
import javax.naming.ldap.Rdn
import javax.security.auth.x500.X500Principal

/**
 * X.500 distinguished name data type customised to how Corda membership uses names.
 *
 * This restricts the attributes to those Corda
 * supports, and requires that organization, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions.
 *
 * The class also guaranties the reliable equality comparison regardless which order the attributes are specified when
 * parsing from the string or X500principal as well outputs the attributes to string in predictable order.
 *
 * There may be additional network specific requirements which need to be taken into account when creating a name by the
 * user.
 * For example, the network operator may require a particular format for names so that they can issue suitable
 * certificates. Finding and giving a suitable name will be the user's responsibility.
 *
 * The order of attributes for building the names is the following: CN, OU, O, L, ST, C
 *
 * Example usages:
 *
 * ```java
 * String commonName = "Alice";
 * String organizationUnit = "Accounting";
 * String organization = "R3";
 * String locality = "New York";
 * String state = "New York";
 * String country = "US";
 *
 *
 * MemberX500Name exampleNameFirst = new MemberX500Name(organization, locality, country);
 * MemberX500Name exampleNameSecond = new MemberX500Name(commonName, organizationUnit, organization, locality, state, country);
 * MemberX500Name exampleNameThird = new MemberX500Name(commonName, organization, locality, country);
 *
 * String commonNameForExampleNameThird = exampleNameThird.getCommonName();
 * String organizationUnitForExampleNameThird = exampleNameThird.getOrganisationUnit();
 * String organizationForExampleNameThird = exampleNameThird.getOrganisation();
 * String localityForExampleNameThird = exampleNameThird.getLocality();
 * String stateForExampleNameThird = exampleNameThird.getState();
 * String countryForExampleNameThird = exampleNameThird.getCountry();
 * X500Principal principalForExampleNameThird = exampleNameThird.getX500Principal();
 *
 * String name = "O=organization,L=London,C=GB";
 * X500Principal principalForNewName = new X500Principal(name);
 * MemberX500Name nameByPrincipal = MemberX500Name.build(principalForNewName);
 * MemberX500Name nameByParse = MemberX500Name.parse(name);
 *
 * Map<String, String > map = MemberX500Name.toAttributesMap("CN=alice, OU=Accounting, O=R3, L=Seattle, ST=Washington, C=US");
 * ```
 *
 * ```kotlin
 * val commonName = "Alice"
 * val organizationUnit = "Accounting"
 * val organization = "R3"
 * val locality = "New York"
 * val state = "New York"
 * val country = "US"
 *
 * val exampleNameFirst = MemberX500Name(organization, locality, country)
 * val exampleNameSecond = MemberX500Name(commonName, organizationUnit, organization, locality, state, country)
 * val exampleNameThird = MemberX500Name(commonName, organization, locality, country)
 *
 * val commonNameForExampleNameThird = exampleNameThird.commonName
 * val organizationUnitForExampleNameThird = exampleNameThird.organizationUnit
 * val organizationForExampleNameThird = exampleNameThird.organization
 * val localityForExampleNameThird = exampleNameThird.locality
 * val stateForExampleNameThird = exampleNameThird.state
 * val countryForExampleNameThird = exampleNameThird.country
 * val principalForExampleNameThird = exampleNameThird.x500Principal
 * val name = "O=organization,L=London,C=GB"
 * val principalForNewName = X500Principal(name)
 * val nameByPrincipal = MemberX500Name.build(principalForNewName)
 * val nameByParse = MemberX500Name.parse(name)
 *
 * val map = MemberX500Name.toAttributesMap("CN=alice, OU=Accounting, O=R3, L=Seattle, ST=Washington, C=US")
 * ```
 *
 * @param commonName Summary name by which the entity is usually known. Corresponds to the "CN" attribute type.
 * @param organizationUnit Name of a unit within the [organization], typically the department, or business unit.
 * Corresponds to the "OU" attribute type.
 * @param organization Name of the organization, typically the company name. Corresponds to the "O" attribute type.
 * @param locality Locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
 * @param state The full name of the state or province the organization is based in. Corresponds to the "ST"
 * attribute type.
 * @param country Country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
 * attribute type.
*/
@Suppress("LongParameterList")
@CordaSerializable
class MemberX500Name(
    /**
     * Optional field, summary name by which the entity is usually known. Corresponds to the "CN" attribute type.
     * Null, if not provided.
     */
    val commonName: String?,
    /**
     * Optional field, name of a unit within the [organization], typically the department, or business unit.
     * Corresponds to the "OU" attribute type. Null, if not provided.
     */
    val organizationUnit: String?,
    /**
     * Mandatory field, name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * Must be provided.
     */
    val organization: String,
    /**
     * Mandatory field, locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * Must be provided.
     */
    val locality: String,
    /**
     * Optional field, the full name of the state or province the organization is based in. Corresponds to the "ST"
     * attribute type. Null, if not provided.
     */
    val state: String?,
    /**
     * Mandatory field, country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type. Must be provided.
     */
    val country: String
) : Comparable<MemberX500Name> {
    companion object {
        /** Max length for organization. */
        const val MAX_LENGTH_ORGANIZATION = 128
        /** Max length for locality. */
        const val MAX_LENGTH_LOCALITY = 64
        /** Max length for state. */
        const val MAX_LENGTH_STATE = 64
        /** Max length for organization unit. */
        const val MAX_LENGTH_ORGANIZATION_UNIT = 64
        /** Max length for common name. */
        const val MAX_LENGTH_COMMON_NAME = 64

        private const val ATTRIBUTE_COMMON_NAME = "CN"
        private const val ATTRIBUTE_ORGANIZATION_UNIT = "OU"
        private const val ATTRIBUTE_ORGANIZATION = "O"
        private const val ATTRIBUTE_LOCALITY = "L"
        private const val ATTRIBUTE_STATE = "ST"
        private const val ATTRIBUTE_COUNTRY = "C"

        private const val UNSPECIFIED_COUNTRY = "ZZ"

        private val supportedAttributes = setOf(
            ATTRIBUTE_COMMON_NAME,
            ATTRIBUTE_ORGANIZATION_UNIT,
            ATTRIBUTE_ORGANIZATION,
            ATTRIBUTE_LOCALITY,
            ATTRIBUTE_STATE,
            ATTRIBUTE_COUNTRY
        )

        private val countryCodes: Set<String> = Locale.getISOCountries().toSet() + UNSPECIFIED_COUNTRY

        private val comparator by lazy {
            compareBy<MemberX500Name>(
                { it.commonName },
                { it.organizationUnit },
                { it.organization },
                { it.locality },
                { it.state },
                { it.country },
            )
        }

        /**
         * Creates an instance of [MemberX500Name] from specified [X500Principal].
         *
         * @param principal The X500 principal used for building [MemberX500Name].
         *
         * @param principal X500Principal to build X500 name from.
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied.
         *
         * @return [MemberX500Name] based on [principal].
         */
        @JvmStatic
        fun build(principal: X500Principal): MemberX500Name = parse(toAttributesMap(principal))

        /**
         * Creates an instance of [MemberX500Name] by parsing the string representation of X500 name.
         *
         * Expects a string representation like "CN=Alice, OU=Engineering, O=R3, L=London, C=GB".
         * Constrains are the same as for [toAttributesMap] plus some additional constrains:
         * - O, L, C are required attributes.
         *
         * @param name The string representation of the name.
         *
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied or
         * the name is improperly specified.
         *
         * @return [MemberX500Name] based on [name].
         */
        @JvmStatic
        fun parse(name: String): MemberX500Name = parse(toAttributesMap(name))

        /**
         * Parses the string representation of X500 name and builds the attribute map.
         *
         * The key is the attributes keys, like CN, O, etc.
         * Constraints:
         * - The RDNs cannot be multivalued
         * - The attributes must have single value
         * - The only supported attributes are C, ST, L, O, OU, CN
         * - Attributes cannot be duplicated
         *
         * @param name The string representation to build the attribute map from.
         *
         * @throws [IllegalArgumentException] if required attributes are missing, constrains are not satisfied or
         * the name is improperly specified.
         *
         * @return The attribute map parsed from the [name].
         */
        @JvmStatic
        fun toAttributesMap(name: String): Map<String, String> {
            // X500Principal is used to sanitise the syntax as the LdapName will let through such string as
            // "O=VALID, L=IN,VALID, C=DE, OU=VALID, CN=VALID" where the (L) have to be escaped
            return toAttributesMap(X500Principal(name))
        }

        private fun parse(attrsMap: Map<String, String>): MemberX500Name {
            val cn = attrsMap[ATTRIBUTE_COMMON_NAME]
            val ou = attrsMap[ATTRIBUTE_ORGANIZATION_UNIT]
            val o = requireNotNull(attrsMap[ATTRIBUTE_ORGANIZATION]) { "Member X.500 names must include an O attribute" }
            val l = requireNotNull(attrsMap[ATTRIBUTE_LOCALITY]) { "Member X.500 names must include an L attribute" }
            val st = attrsMap[ATTRIBUTE_STATE]
            val c = requireNotNull(attrsMap[ATTRIBUTE_COUNTRY]) { "Member X.500 names must include an C attribute" }
            return MemberX500Name(cn, ou, o, l, st, c)
        }

        private fun toAttributesMap(principal: X500Principal): Map<String, String> {
            val result = mutableMapOf<String, String>()
            try {
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
            } catch (e: InvalidNameException) {
                throw IllegalArgumentException(e.message, e)
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
     * @param commonName Summary name by which the entity is usually known.
     * @param organization Name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * @param locality Locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * @param country Country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type.
     */
    constructor(commonName: String, organization: String, locality: String, country: String) :
            this(
                commonName = commonName,
                organizationUnit = null,
                organization = organization,
                locality = locality,
                state = null,
                country = country
            )

    /**
     * @param organization Name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * @param locality Locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * @param country Country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type.
     */
    constructor(organization: String, locality: String, country: String) :
            this(null, null, organization, locality, null, country)

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

        require(organization.isNotBlank()) {
            "Organization attribute (O) if specified then it must be not blank."
        }
        require(organization.length < MAX_LENGTH_ORGANIZATION) {
            "Organization attribute (O) must contain less then $MAX_LENGTH_ORGANIZATION characters."
        }

        organizationUnit?.let {
            require(it.isNotBlank()) {
                "Organization unit attribute (OU) if specified then it must be not blank."
            }
            require(it.length < MAX_LENGTH_ORGANIZATION_UNIT) {
                "Organization Unit attribute (OU) must contain less then $MAX_LENGTH_ORGANIZATION_UNIT characters."
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
     * C, ST, L, O, OU, CN (the printing order would be reversed).
     *
     * @throws IllegalArgumentException If a valid RDN cannot be constructed using the given attributes.
     */
    val x500Principal: X500Principal by lazy(LazyThreadSafetyMode.PUBLICATION) {
        try {
            val rdns = mutableListOf<Rdn>().apply {
                add(Rdn(BasicAttributes(ATTRIBUTE_COUNTRY, country)))
                state?.let {
                    add(Rdn(BasicAttributes(ATTRIBUTE_STATE, it)))
                }
                add(Rdn(BasicAttributes(ATTRIBUTE_LOCALITY, locality)))
                add(Rdn(BasicAttributes(ATTRIBUTE_ORGANIZATION, organization)))
                organizationUnit?.let {
                    add(Rdn(BasicAttributes(ATTRIBUTE_ORGANIZATION_UNIT, it)))
                }
                commonName?.let {
                    add(Rdn(BasicAttributes(ATTRIBUTE_COMMON_NAME, it)))
                }
            }
            X500Principal(LdapName(rdns).toString())
        } catch (e: InvalidNameException) {
            throw IllegalArgumentException(e.message, e)
        }
    }

    /**
     * Returns the string equivalent of this name where the order of RDNs is CN, OU, O, L, ST, C.
     */
    override fun toString(): String = x500Principal.toString()

    /**
     * Compares this X500 names to another MemberX500Name.
     */
    override fun compareTo(other: MemberX500Name): Int {
        return comparator.compare(this, other)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemberX500Name

        if (commonName != other.commonName) return false
        if (organizationUnit != other.organizationUnit) return false
        if (organization != other.organization) return false
        if (locality != other.locality) return false
        if (state != other.state) return false
        if (country != other.country) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(
            commonName,
            organizationUnit,
            organization,
            locality,
            state,
            country
        )
    }
}
