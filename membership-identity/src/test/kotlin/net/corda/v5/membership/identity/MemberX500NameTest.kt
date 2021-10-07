package net.corda.v5.membership.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MemberX500NameTest {
    companion object {
        private val commonName = "Service Name"
        private val organisationUnit = "Org Unit"
        private val organisation = "Bank A"
        private val locality = "New York"
        private val country = "US"
    }

    @Test
    fun `service name with organisational unit`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        assertEquals(commonName, name.commonName)
        assertEquals(organisationUnit, name.organisationUnit)
        assertEquals(organisation, name.organisation)
        assertEquals(locality, name.locality)
        assertEquals(MemberX500Name.parse(name.toString()), name)
        assertEquals(MemberX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `service name`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US, CN=Service Name")
        assertEquals(commonName, name.commonName)
        assertNull(name.organisationUnit)
        assertEquals(organisation, name.organisation)
        assertEquals(locality, name.locality)
        assertEquals(MemberX500Name.parse(name.toString()), name)
        assertEquals(MemberX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `legal entity name`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US")
        assertNull(name.commonName)
        assertNull(name.organisationUnit)
        assertEquals(organisation, name.organisation)
        assertEquals(locality, name.locality)
        assertEquals(MemberX500Name.parse(name.toString()), name)
        assertEquals(MemberX500Name.build(name.x500Principal), name)
    }

    @Test
    fun `rejects name with no organisation`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("L=New York, C=US, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with no locality`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=Bank A, C=US, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with no country`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=Bank A, L=New York, OU=Org Unit, CN=Service Name")
        }
    }

    @Test
    fun `rejects name with unsupported attribute`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, SN=blah")
        }
    }

    @Test
    fun `rejects organisation (but not other attributes) with non-latin letters`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=Bཛྷa, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        // doesn't throw
        validateLocalityAndOrganisationalUnitAndCommonName("Bཛྷa")
    }

    @Test
    fun `organisation (but not other attributes) must have at least two letters`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=B, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        }
        // doesn't throw
        validateLocalityAndOrganisationalUnitAndCommonName("B")
        validateLocalityAndOrganisationalUnitAndCommonName("")
    }

    @Test
    fun `accepts attributes starting with lower case letter`() {
        MemberX500Name.parse("O=bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        validateLocalityAndOrganisationalUnitAndCommonName("bank")
    }

    @Test
    fun `accepts attributes starting with numeric character`() {
        MemberX500Name.parse("O=8Bank A, L=New York, C=DE, OU=Org Unit, CN=Service Name")
        validateLocalityAndOrganisationalUnitAndCommonName("8bank")
    }

    @Test
    fun `accepts attributes with leading whitespace`() {
        MemberX500Name.parse("O= VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        validateLocalityAndOrganisationalUnitAndCommonName(" VALID")
    }

    @Test
    fun `accepts attributes with trailing whitespace`() {
        MemberX500Name.parse("O=VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        validateLocalityAndOrganisationalUnitAndCommonName("VALID ")
    }

    @Test
    fun `rejects attributes with comma`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=IN,VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN,VALID")
    }

    @Test
    fun `accepts org with equals sign`() {
        MemberX500Name.parse("O=IN=VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
    }

    @Test
    fun `accepts organisation with dollar sign`() {
        MemberX500Name.parse("O=VA\$LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        validateLocalityAndOrganisationalUnitAndCommonName("VA\$LID")
    }

    @Test
    fun `rejects attributes with double quotation mark`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=IN\"VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN\"VALID")
    }

    @Test
    fun `accepts organisation with single quotation mark`() {
        MemberX500Name.parse("O=VA'LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        validateLocalityAndOrganisationalUnitAndCommonName("VA'LID")
    }

    @Test
    fun `rejects organisation with backslash`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=IN\\VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        checkLocalityAndOrganisationalUnitAndCommonNameReject("IN\\VALID")
    }

    @Test
    fun `rejects double spacing only in the organisation attribute`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=IN  VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        validateLocalityAndOrganisationalUnitAndCommonName("VA  LID")
    }

    @Test
    fun `rejects organisation (but not other attributes) containing the null character`() {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=IN${Character.MIN_VALUE}VALID , L=VALID, C=DE, OU=VALID, CN=VALID")
        }
        validateLocalityAndOrganisationalUnitAndCommonName("VA${Character.MIN_VALUE}LID")
    }

    @Test
    fun `create MemberX500Name without organisationUnit and state`() {
        val member = MemberX500Name(commonName, organisation, locality, country)
        assertEquals(commonName, member.commonName)
        assertNull(member.organisationUnit)
        assertEquals(organisation, member.organisation)
        assertEquals(locality, member.locality)
        assertNull(member.state)
        assertEquals(country, member.country)
    }

    @Test
    fun `create MemberX500Name without commonName, organisationUnit and state`() {
        val member = MemberX500Name(organisation, locality, country)
        assertNull(member.commonName)
        assertNull(member.organisationUnit)
        assertEquals(organisation, member.organisation)
        assertEquals(locality, member.locality)
        assertNull(member.state)
        assertEquals(country, member.country)
    }

    private fun checkLocalityAndOrganisationalUnitAndCommonNameReject(invalid: String) {
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=VALID, L=${invalid}, C=DE, OU=VALID, CN=VALID")
        }
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=VALID, L=VALID, C=DE, OU=${invalid}, CN=VALID")
        }
        assertFailsWith(IllegalArgumentException::class) {
            MemberX500Name.parse("O=VALID, L=VALID, C=DE, OU=VALID, CN=${invalid}")
        }
    }

    private fun validateLocalityAndOrganisationalUnitAndCommonName(valid: String) {
        MemberX500Name.parse("O=VALID, L=${valid}, C=DE, OU=VALID, CN=VALID")
        MemberX500Name.parse("O=VALID, L=VALID, C=DE, OU=${valid}, CN=VALID")
        MemberX500Name.parse("O=VALID, L=VALID, C=DE, OU=VALID, CN=${valid}")
    }
}