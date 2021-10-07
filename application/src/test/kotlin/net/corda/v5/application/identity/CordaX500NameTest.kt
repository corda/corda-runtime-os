package net.corda.v5.application.identity

import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith

class CordaX500NameTest {
    companion object {
        private val commonName = "Service Name"
        private val organisationUnit = "Org Unit"
        private val organisation = "Bank A"
        private val locality = "New York"
        private val country = "US"
    }

    @Test
    fun `create CordaX500Name without organisationUnit and state`() {
        val x500Name = CordaX500Name(commonName, organisation, locality, country)
        assertEquals(commonName, x500Name.commonName)
        assertNull(x500Name.organisationUnit)
        assertEquals(organisation, x500Name.organisation)
        assertEquals(locality, x500Name.locality)
        assertNull(x500Name.state)
        assertEquals(country, x500Name.country)
    }

    @Test
    fun `create CordaX500Name without commonName, organisationUnit and state`() {
        val x500Name = CordaX500Name(organisation, locality, country)
        assertNull(x500Name.commonName)
        assertNull(x500Name.organisationUnit)
        assertEquals(organisation, x500Name.organisation)
        assertEquals(locality, x500Name.locality)
        assertNull(x500Name.state)
        assertEquals(country, x500Name.country)
    }

    @Test
    fun `create CordaX500Name using MemberX500Name`() {
        val memberX500Name = MemberX500Name(organisation, locality, country)
        val cordaX500Name = CordaX500Name(memberX500Name)
        assertEquals(memberX500Name.organisation, cordaX500Name.organisation)
        assertEquals(memberX500Name.locality, cordaX500Name.locality)
        assertEquals(memberX500Name.country, cordaX500Name.country)
    }

    @Test
    fun `create CordaX500Name should fail due to invalid country code`() {
        assertFailsWith<IllegalArgumentException>("Invalid country code XX", { CordaX500Name(organisation, locality, "XX") })
    }

    @Test
    fun `parse function creates CordaX500Name`() {
        val name = CordaX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        assertEquals(commonName, name.commonName)
        assertEquals(organisationUnit, name.organisationUnit)
        assertEquals(organisation, name.organisation)
        assertEquals(locality, name.locality)
        assertEquals(CordaX500Name.parse(name.toString()), name)
        assertEquals(CordaX500Name.build(name.x500Principal), name)
    }
}