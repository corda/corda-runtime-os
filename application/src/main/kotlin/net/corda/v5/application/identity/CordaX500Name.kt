package net.corda.v5.application.identity

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.membership.identity.MemberX500Name
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
@Suppress("LongParameterList")
@CordaSerializable
class CordaX500Name(
    commonName: String?,
    organisationUnit: String?,
    organisation: String,
    locality: String,
    state: String?,
    country: String
) : MemberX500Name(commonName, organisationUnit, organisation, locality, state, country) {
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
    constructor(organisation: String, locality: String, country: String)
            : this(null, null, organisation, locality, null, country)

    /**
     * @param memberX500Name the [MemberX500Name] we want to copy as [CordaX500Name]
     */
    constructor(memberX500Name: MemberX500Name) :
            this(
                memberX500Name.commonName,
                memberX500Name.organisationUnit,
                memberX500Name.organisation,
                memberX500Name.locality,
                memberX500Name.state,
                memberX500Name.country
            )

    companion object {
        @JvmStatic
        fun build(principal: X500Principal): CordaX500Name {
            return CordaX500Name(MemberX500Name.build(principal))
        }

        @JvmStatic
        fun parse(name: String): CordaX500Name = build(X500Principal(name))
    }
}