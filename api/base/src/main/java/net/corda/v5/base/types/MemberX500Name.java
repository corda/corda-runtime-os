package net.corda.v5.base.types;

import net.corda.v5.base.annotations.ConstructorForDeserialization;
import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

import static java.util.Comparator.comparing;

/**
 * X.500 distinguished name data type customised to how Corda membership uses names.
 * <p>
 * This restricts the attributes to those Corda
 * supports, and requires that organization, locality and country attributes are specified. See also RFC 4519 for
 * the underlying attribute type definitions.
 * <p>
 * The class also guaranties the reliable equality comparison regardless which order the attributes are specified when
 * parsing from the string or X500principal as well outputs the attributes to string in predictable order.
 * <p>
 * There may be additional network specific requirements which need to be taken into account when creating a name by the
 * user.
 * For example, the network operator may require a particular format for names so that they can issue suitable
 * certificates. Finding and giving a suitable name will be the user's responsibility.
 * <p>
 * The order of attributes for building the names is the following: CN, OU, O, L, ST, C
 * <p>
 * Example usages:
 * <ul>
 * <li><pre>{@code
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
 * }</pre></li>
 * <li>Kotlin:<pre>{@code
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
 * }</pre></li>
 * </ul>
 */
@CordaSerializable
public final class MemberX500Name implements Comparable<MemberX500Name> {
    /** Max length for organization. */
    public static final int MAX_LENGTH_ORGANIZATION = 128;
    /** Max length for locality. */
    public static final int MAX_LENGTH_LOCALITY = 64;
    /** Max length for state. */
    public static final int MAX_LENGTH_STATE = 64;
    /** Max length for organization unit. */
    public static final int MAX_LENGTH_ORGANIZATION_UNIT = 64;
    /** Max length for common name. */
    public static final int MAX_LENGTH_COMMON_NAME = 64;

    private static final String ATTRIBUTE_COMMON_NAME = "CN";
    private static final String ATTRIBUTE_ORGANIZATION_UNIT = "OU";
    private static final String ATTRIBUTE_ORGANIZATION = "O";
    private static final String ATTRIBUTE_LOCALITY = "L";
    private static final String ATTRIBUTE_STATE = "ST";
    private static final String ATTRIBUTE_COUNTRY = "C";

    private static final String UNSPECIFIED_COUNTRY = "ZZ";

    private static final Set<String> supportedAttributes = Set.of(
        ATTRIBUTE_COMMON_NAME,
        ATTRIBUTE_ORGANIZATION_UNIT,
        ATTRIBUTE_ORGANIZATION,
        ATTRIBUTE_LOCALITY,
        ATTRIBUTE_STATE,
        ATTRIBUTE_COUNTRY
    );

    private static final Set<String> countryCodes;
    static {
        Set<String> codes = new LinkedHashSet<>();
        Collections.addAll(codes, Locale.getISOCountries());
        codes.add(UNSPECIFIED_COUNTRY);
        countryCodes = Collections.unmodifiableSet(codes);
    }

    @NotNull
    private static <T> T requireNotNull(@Nullable T obj, @NotNull String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    @SuppressWarnings("StringEquality")
    private static int compareToNull(String a, String b) {
        if (a == b) {
            return 0;
        } else if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        } else {
            return a.compareTo(b);
        }
    }

    private static final Comparator<MemberX500Name> comparator = comparing(MemberX500Name::getCommonName, MemberX500Name::compareToNull)
        .thenComparing(MemberX500Name::getOrganizationUnit, MemberX500Name::compareToNull)
        .thenComparing(MemberX500Name::getOrganization)
        .thenComparing(MemberX500Name::getLocality)
        .thenComparing(MemberX500Name::getState, MemberX500Name::compareToNull)
        .thenComparing(MemberX500Name::getCountry);

    /**
     * Creates an instance of {@link MemberX500Name} from specified {@link X500Principal}.
     *
     * @param principal The X500 principal used for building {@link MemberX500Name}.
     * @throws IllegalArgumentException if required attributes are missing, constrains are not satisfied.
     *
     * @return {@link MemberX500Name} based on {@code principal}.
     */
    @NotNull
    public static MemberX500Name build(@NotNull X500Principal principal) {
        requireNotNull(principal, "principal must not be null");
        return parse(toAttributesMap(principal));
    }

    /**
     * Creates an instance of {@link MemberX500Name} by parsing the string representation of X500 name.
     * <p>
     * Expects a string representation like "CN=Alice, OU=Engineering, O=R3, L=London, C=GB".
     * Constrains are the same as for {@link #toAttributesMap} plus some additional constrains:
     * - O, L, C are required attributes.
     *
     * @param name The string representation of the name.
     *
     * @throws IllegalArgumentException if required attributes are missing, constrains are not satisfied or
     * the name is improperly specified.
     *
     * @return {@link MemberX500Name} based on {@code name}.
     */
    @NotNull
    public static MemberX500Name parse(@NotNull String name) {
        requireNotNull(name, "x500Name must not be null");
        return parse(toAttributesMap(name));
    }

    /**
     * Parses the string representation of X500 name and builds the attribute map.
     * <p>
     * The key is the attributes keys, like CN, O, etc.
     * Constraints:
     * <ul>
     * <li>The RDNs cannot be multivalued</li>
     * <li>The attributes must have single value</li>
     * <li>The only supported attributes are C, ST, L, O, OU, CN</li>
     * <li>Attributes cannot be duplicated</li>
     * </ul>
     * @param name The string representation to build the attribute map from.
     *
     * @throws IllegalArgumentException if required attributes are missing, constrains are not satisfied or
     * the name is improperly specified.
     *
     * @return The attribute map parsed from the {@code name}.
     */
    @NotNull
    public static Map<String, String> toAttributesMap(@NotNull String name) {
        // X500Principal is used to sanitise the syntax as the LdapName will let through such string as
        // "O=VALID, L=IN,VALID, C=DE, OU=VALID, CN=VALID" where the (L) have to be escaped
        requireNotNull(name, "name must not be null");
        return toAttributesMap(new X500Principal(name));
    }

    @NotNull
    private static MemberX500Name parse(@NotNull Map<String, String> attrsMap) {
        requireNotNull(attrsMap, "attrsMap must not be null");
        String cn = attrsMap.get(ATTRIBUTE_COMMON_NAME);
        String ou = attrsMap.get(ATTRIBUTE_ORGANIZATION_UNIT);
        String o = requireNotNull(attrsMap.get(ATTRIBUTE_ORGANIZATION), "Member X.500 names must include an O attribute");
        String l = requireNotNull(attrsMap.get(ATTRIBUTE_LOCALITY), "Member X.500 names must include an L attribute");
        String st = attrsMap.get(ATTRIBUTE_STATE);
        String c = requireNotNull(attrsMap.get(ATTRIBUTE_COUNTRY), "Member X.500 names must include an C attribute");
        return new MemberX500Name(cn, ou, o, l, st, c);
    }

    @NotNull
    private static Map<String, String> toAttributesMap(@NotNull X500Principal principal) {
        requireNotNull(principal, "principal must not be null");

        Map<String, String> result = new LinkedHashMap<>();
        try {
            for (Rdn rdn : new LdapName(principal.toString()).getRdns()) {
                if (rdn.size() != 1) {
                    throw new IllegalArgumentException("The RDN '" + rdn + "' must not be multi-valued.");
                }
                Enumeration<? extends Attribute> attrs = rdn.toAttributes().getAll();
                while (attrs.hasMoreElements()) {
                    Attribute attr = attrs.nextElement();
                    if (attr.size() != 1) {
                        throw new IllegalArgumentException("Attribute '" + attr.getID() + "' have to contain only single value.");
                    }
                    Object value = attr.get(0);
                    if (!(value instanceof String)) {
                        throw new IllegalArgumentException("Attribute's '" + attr.getID() + "' value must be a string");
                    }
                    if (result.containsKey(attr.getID())) {
                        throw new IllegalArgumentException("Duplicate attribute " + attr.getID());
                    }
                    result.put(attr.getID(), (String) value);
                }
            }
        } catch (NamingException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        if (!supportedAttributes.isEmpty()) {
            final Set<String> unsupported = new LinkedHashSet<>(result.keySet());
            unsupported.removeAll(supportedAttributes);
            if (!unsupported.isEmpty()) {
                throw new IllegalArgumentException("The following attribute" + (unsupported.size() > 1 ? "s are" : " is")
                        + " not supported in Corda: " + String.join(",", unsupported));
            }
        }

        return result;
    }

    private final String commonName;
    private final String organizationUnit;
    private final String organization;
    private final String locality;
    private final String state;
    private final String country;

    /**
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
    @ConstructorForDeserialization
    public MemberX500Name(
        @Nullable String commonName,
        @Nullable String organizationUnit,
        @NotNull String organization,
        @NotNull String locality,
        @Nullable String state,
        @NotNull String country
    ) {
        requireNotNull(organization, "Organization should not be null");
        requireNotNull(locality, "locality should not be null");
        requireNotNull(country, "country should not be null");

        if (!countryCodes.contains(country)) {
            throw new IllegalArgumentException("Invalid country code " + country);
        }
        this.country = country;

        if (state != null) {
            state = state.trim();
            if (state.isEmpty()) {
                throw new IllegalArgumentException("State attribute (ST) if specified then it must be not blank.");
            }
            if (state.length() >= MAX_LENGTH_STATE) {
                throw new IllegalArgumentException("State attribute (ST) must contain no more then " + MAX_LENGTH_STATE + " characters.");
            }
        }
        this.state = state;

        this.locality = locality.trim();
        if (this.locality.isEmpty()) {
            throw new IllegalArgumentException("Locality attribute (L) must not be blank.");
        }
        if (this.locality.length() >= MAX_LENGTH_LOCALITY) {
            throw new IllegalArgumentException("Locality attribute (L) must contain no more than " + MAX_LENGTH_LOCALITY + " characters.");
        }

        this.organization = organization.trim();
        if (this.organization.isEmpty()) {
            throw new IllegalArgumentException("Organization attribute (O) if specified then it must be not blank.");
        }
        if (organization.length() >= MAX_LENGTH_ORGANIZATION) {
            throw new IllegalArgumentException("Organisation attribute (O) must contain no more then " + MAX_LENGTH_ORGANIZATION + " characters.");
        }

        if (organizationUnit != null) {
            organizationUnit = organizationUnit.trim();
            if (organizationUnit.isEmpty()) {
                throw new IllegalArgumentException("Organization unit attribute (OU) if specified then it must be not blank.");
            }
            if (organizationUnit.length() >= MAX_LENGTH_ORGANIZATION_UNIT) {
                throw new IllegalArgumentException("Organization Unit attribute (OU) must contain no more then " + MAX_LENGTH_ORGANIZATION_UNIT + " characters.");
            }
        }
        this.organizationUnit = organizationUnit;

        if (commonName != null) {
            commonName = commonName.trim();
            if (commonName.isEmpty()) {
                throw new IllegalArgumentException("Common name attribute (CN) must not be blank.");
            }
            if (commonName.length() >= MAX_LENGTH_COMMON_NAME) {
                throw new IllegalArgumentException("Common Name attribute (CN) must contain no more then " + MAX_LENGTH_COMMON_NAME + " characters.");
            }
        }
        this.commonName = commonName;
    }

    /**
     * @param commonName Summary name by which the entity is usually known.
     * @param organization Name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * @param locality Locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * @param country Country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type.
     */
    public MemberX500Name(
        @NotNull String commonName,
        @NotNull String organization,
        @NotNull String locality,
        @NotNull String country
    ) {
        this(commonName, null, organization, locality, null, country);
    }

    /**
     * @param organization Name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * @param locality Locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * @param country Country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type.
     */
    public MemberX500Name(
        @NotNull String organization,
        @NotNull String locality,
        @NotNull String country
    ) {
        this(null, null, organization, locality, null, country);
    }

    /**
     * Optional field, summary name by which the entity is usually known. Corresponds to the "CN" attribute type.
     * Null, if not provided.
     */
    @Nullable
    public String getCommonName() {
        return commonName;
    }

    /**
     * Optional field, name of a unit within the [organization], typically the department, or business unit.
     * Corresponds to the "OU" attribute type. Null, if not provided.
     */
    @Nullable
    public String getOrganizationUnit() {
        return organizationUnit;
    }

    /**
     * Mandatory field, name of the organization, typically the company name. Corresponds to the "O" attribute type.
     * Must be provided.
     */
    @NotNull
    public String getOrganization() {
        return organization;
    }

    /**
     * Mandatory field, locality of the organization, typically the nearest major city. Corresponds to the "L" attribute type.
     * Must be provided.
     */
    @NotNull
    public String getLocality() {
        return locality;
    }

    /**
     * Optional field, the full name of the state or province the organization is based in. Corresponds to the "ST"
     * attribute type. Null, if not provided.
     */
    @Nullable
    public String getState() {
        return state;
    }

    /**
     * Mandatory field, country the organization is in, as an ISO 3166-1 2-letter country code. Corresponds to the "C"
     * attribute type. Must be provided.
     */
    @NotNull
    public String getCountry() {
        return country;
    }

    private X500Principal _x500Principal;

    /**
     * Returns the {@link X500Principal} equivalent of this name where the order of RDNs is
     * C, ST, L, O, OU, CN (the printing order would be reversed).
     *
     * @throws IllegalArgumentException If a valid RDN cannot be constructed using the given attributes.
     */
    @NotNull
    public synchronized X500Principal getX500Principal() {
        if (_x500Principal == null) {
            _x500Principal = createX500Principal();
        }
        return _x500Principal;
    }

    @NotNull
    private X500Principal createX500Principal() {
        final List<Rdn> rdns = new ArrayList<>();
        try {
            rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_COUNTRY, country)));
            if (state != null) {
                rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_STATE, state)));
            }
            rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_LOCALITY, locality)));
            rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_ORGANIZATION, organization)));
            if (organizationUnit != null) {
                rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_ORGANIZATION_UNIT, organizationUnit)));
            }
            if (commonName != null) {
                rdns.add(new Rdn(new BasicAttributes(ATTRIBUTE_COMMON_NAME, commonName)));
            }
        } catch (InvalidNameException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        return new X500Principal(new LdapName(rdns).toString());
    }

    /**
     * Returns the string equivalent of this name where the order of RDNs is CN, OU, O, L, ST, C
     */
    @Override
    @NotNull
    public String toString() {
        return getX500Principal().toString();
    }

    /**
     * Compares this X500 name to another {@link MemberX500Name}.
     */
    @Override
    public int compareTo(@NotNull MemberX500Name other) {
        requireNotNull(other, "Cannot compare to a null MemberX500Name");
        return comparator.compare(this, other);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final MemberX500Name other = (MemberX500Name) obj;
        return Objects.equals(commonName, other.commonName)
            && Objects.equals(organizationUnit, other.organizationUnit)
            && Objects.equals(organization, other.organization)
            && Objects.equals(locality, other.locality)
            && Objects.equals(state, other.state)
            && Objects.equals(country, other.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            commonName,
            organizationUnit,
            organization,
            locality,
            state,
            country
        );
    }
}
