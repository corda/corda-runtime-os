package net.corda.v5.base.types;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.util.Map;

import static java.util.Map.entry;

class MemberX500NameJavaApiTest {

    private final String commonName = "commonName";
    private final String organisationUnit = "organisationUnit";
    private final String organization = "organization";
    private final String locality = "London";
    private final String state = "state";
    private final String country = "GB";

    private final String name = "O=organization,L=London,C=GB";

    @Test
    public void requiredArguments() {
        final MemberX500Name cordaX500Name = new MemberX500Name(organization, locality, country);

        Assertions.assertThat(cordaX500Name.getCommonName()).isNull();
        Assertions.assertThat(cordaX500Name.getOrganisationUnit()).isNull();
        Assertions.assertThat(cordaX500Name.getOrganisation()).isEqualTo(organization);
        Assertions.assertThat(cordaX500Name.getLocality()).isEqualTo(locality);
        Assertions.assertThat(cordaX500Name.getState()).isNull();
        Assertions.assertThat(cordaX500Name.getCountry()).isEqualTo(country);
    }

    @Test
    public void allArguments() {
        final MemberX500Name cordaX500Name = new MemberX500Name(
                commonName, organisationUnit, organization, locality, state, country
        );

        Assertions.assertThat(cordaX500Name.getCommonName()).isEqualTo(commonName);
        Assertions.assertThat(cordaX500Name.getOrganisationUnit()).isEqualTo(organisationUnit);
        Assertions.assertThat(cordaX500Name.getOrganisation()).isEqualTo(organization);
        Assertions.assertThat(cordaX500Name.getLocality()).isEqualTo(locality);
        Assertions.assertThat(cordaX500Name.getState()).isEqualTo(state);
        Assertions.assertThat(cordaX500Name.getCountry()).isEqualTo(country);

        Assertions.assertThat(cordaX500Name.getX500Principal()).isNotNull();
    }

    @Test
    public void constructor_withCommonNameOrganizationLocalityAndCountry() {
        final MemberX500Name cordaX500Name = new MemberX500Name(
                commonName, organization, locality, country
        );

        Assertions.assertThat(cordaX500Name.getCommonName()).isEqualTo(commonName);
        Assertions.assertThat(cordaX500Name.getOrganisationUnit()).isNull();
        Assertions.assertThat(cordaX500Name.getOrganisation()).isEqualTo(organization);
        Assertions.assertThat(cordaX500Name.getLocality()).isEqualTo(locality);
        Assertions.assertThat(cordaX500Name.getState()).isNull();
        Assertions.assertThat(cordaX500Name.getCountry()).isEqualTo(country);
    }

    @Test
    public void build() {
        final X500Principal principal = new X500Principal(name);
        final MemberX500Name cordaX500Name = MemberX500Name.build(principal);

        Assertions.assertThat(cordaX500Name.getOrganisation()).isEqualTo(organization);
        Assertions.assertThat(cordaX500Name.getLocality()).isEqualTo(locality);
        Assertions.assertThat(cordaX500Name.getCountry()).isEqualTo(country);
    }

    @Test
    public void parse() {
        final MemberX500Name cordaX500Name = MemberX500Name.parse(name);

        Assertions.assertThat(cordaX500Name.getOrganisation()).isEqualTo(organization);
        Assertions.assertThat(cordaX500Name.getLocality()).isEqualTo(locality);
        Assertions.assertThat(cordaX500Name.getCountry()).isEqualTo(country);
    }

    @Test
    public void x500Principal() {
        final MemberX500Name cordaX500Name = new MemberX500Name(organization, locality, country);

        final X500Principal principal = cordaX500Name.getX500Principal();

        Assertions.assertThat(principal).isNotNull();
    }

    @Test
    public void toAttributesMap() {
        final Map<String, String > map = MemberX500Name.toAttributesMap(
                "CN=alice, OU=Accounting, O=R3, L=Seattle, ST=Washington, C=US"
        );
        Assertions.assertThat(map).contains(entry("CN", "alice"));
        Assertions.assertThat(map).contains(entry("OU", "Accounting"));
        Assertions.assertThat(map).contains(entry("O", "R3"));
        Assertions.assertThat(map).contains(entry("L", "Seattle"));
        Assertions.assertThat(map).contains(entry("ST", "Washington"));
        Assertions.assertThat(map).contains(entry("C", "US"));
    }
}