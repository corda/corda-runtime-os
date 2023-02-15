package net.corda.v5.base.types

import net.corda.v5.base.types.MemberX500Name.MAX_LENGTH_COMMON_NAME
import net.corda.v5.base.types.MemberX500Name.MAX_LENGTH_LOCALITY
import net.corda.v5.base.types.MemberX500Name.MAX_LENGTH_ORGANIZATION
import net.corda.v5.base.types.MemberX500Name.MAX_LENGTH_ORGANIZATION_UNIT
import net.corda.v5.base.types.MemberX500Name.MAX_LENGTH_STATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.security.auth.x500.X500Principal
import kotlin.random.Random
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemberX500NameTest {
    private fun randomString(length: Int): String {
        val bytes = ByteArray(length)
        val random = Random(Instant.now().toEpochMilli())
         for (i in bytes.indices) {
             bytes[i] = random.nextInt('A'.code, 'Z'.code).toByte()
         }
        return String(bytes)
    }

    @Test
    fun `Should parse properly escaped X500 name string`() {
        val name = MemberX500Name.parse("O=\"Bank+A\", CN=\"Service<>Name\", L=\"New=York\", C=US")
        assertEquals("Service<>Name", name.commonName)
        assertNull(name.organizationUnit)
        assertNull(name.state)
        assertEquals("Bank+A", name.organization)
        assertEquals("New=York", name.locality)
        assertEquals("US", name.country)
        assertEquals("CN=\"Service<>Name\", O=\"Bank+A\", L=\"New=York\", C=US", name.toString())
    }

    @Test
    fun `Should throw IllegalArgumentException parsing malformed string`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank, A, CN=Service Name, L=New=York, C=US")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException parsing X500 name string with multi-valued RDN`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, CN=Service Name+OU=R3, L=New=York, C=US")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException parsing X500 name string with duplicate attribute`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, CN=Service Name, CN=R3, L=New=York, C=US")
        }
    }

    @Test
    fun `Should output string in predictable order`() {
        val name = MemberX500Name.parse("O=Bank A,L=Seattle,C=US,ST=Washington,CN=Service Name,OU=Org Unit")
        assertEquals("CN=Service Name, OU=Org Unit, O=Bank A, L=Seattle, ST=Washington, C=US", name.toString())
    }

    @Test
    fun `Should parse string without state`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=Service Name")
        assertEquals("Service Name", name.commonName)
        assertEquals("Org Unit", name.organizationUnit)
        assertEquals("Bank A", name.organization)
        assertEquals("New York", name.locality)
        assertEquals("US", name.country)
        assertNull(name.state)
    }

    @Test
    fun `Should parse string containing unspecified country code`() {
        val name = MemberX500Name.parse("O=Organization, L=Location, C=ZZ, OU=Org Unit, CN=Service Name")
        assertEquals("Service Name", name.commonName)
        assertEquals("Org Unit", name.organizationUnit)
        assertEquals("Organization", name.organization)
        assertEquals("Location", name.locality)
        assertEquals("ZZ", name.country)
        assertNull(name.state)
    }

    @Test
    fun `Should parse string without state and organizational unit`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US, CN=Service Name")
        assertEquals("Service Name", name.commonName)
        assertEquals("Bank A", name.organization)
        assertEquals("New York", name.locality)
        assertEquals("US", name.country)
        assertNull(name.state)
        assertNull(name.organizationUnit)
    }

    @Test
    fun `Should parse string without state, organizational unit and common name`() {
        val name = MemberX500Name.parse("O=Bank A, L=New York, C=US")
        assertEquals("Bank A", name.organization)
        assertEquals("New York", name.locality)
        assertEquals("US", name.country)
        assertNull(name.state)
        assertNull(name.organizationUnit)
        assertNull(name.commonName)
    }

    @Test
    fun `Should throw IllegalArgumentException when state is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, ST=, CN=Service Name")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when organizational unit is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=, CN=Service Name")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when common name is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=Org Unit, CN=")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when country is not present`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when country is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when country is not known`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=XXX, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when locality is not present`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, C=US, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when locality is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=, C=US, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when organization is not present`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("L=New York, C=US, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when organization is blank`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=, L=New York, C=US, OU=Org Unit")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when common name exceeds max length`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, CN=${randomString(MAX_LENGTH_COMMON_NAME+1)}")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when organizational unit exceeds max length`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, OU=${randomString(MAX_LENGTH_ORGANIZATION_UNIT+1)}")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when state exceeds max length`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, ST=${randomString(MAX_LENGTH_STATE+1)}")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when locality exceeds max length`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, C=US, L=${randomString(MAX_LENGTH_LOCALITY+1)}")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when organization exceeds max length`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("L=New York, C=US, O=${randomString(MAX_LENGTH_ORGANIZATION+1)}")
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when name has unsupported attribute`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=Bank A, L=New York, C=US, GIVENNAME=blah")
        }
    }

    @Test
    fun `Should parse attributes starting with lower case letter`() {
        val name = MemberX500Name.parse("o=bank A, l=new York, c=DE, ou=org Unit, st=main, cn=service Name")
        assertEquals("bank A", name.organization)
        assertEquals("new York", name.locality)
        assertEquals("DE", name.country)
        assertEquals("main", name.state)
        assertEquals("org Unit", name.organizationUnit)
        assertEquals("service Name", name.commonName)
        assertEquals("CN=service Name, OU=org Unit, O=bank A, L=new York, ST=main, C=DE", name.toString())
    }

    @Test
    fun `Should parse attributes starting with numeric character`() {
        val name = MemberX500Name.parse("O=8Bank A, L=1New York, C=DE, OU=3Org Unit, ST=5main, CN=4Service Name")
        assertEquals("8Bank A", name.organization)
        assertEquals("1New York", name.locality)
        assertEquals("DE", name.country)
        assertEquals("5main", name.state)
        assertEquals("3Org Unit", name.organizationUnit)
        assertEquals("4Service Name", name.commonName)
        assertEquals("CN=4Service Name, OU=3Org Unit, O=8Bank A, L=1New York, ST=5main, C=DE", name.toString())
    }

    @Test
    fun `Should parse attributes with leading whitespace`() {
        val name = MemberX500Name.parse("O= VALID_O, L= VALID_L, C= DE, OU= VALID_OU, CN= VALID_CN, ST= VALID_ST")
        assertEquals("VALID_O", name.organization)
        assertEquals("VALID_L", name.locality)
        assertEquals("DE", name.country)
        assertEquals("VALID_ST", name.state)
        assertEquals("VALID_OU", name.organizationUnit)
        assertEquals("VALID_CN", name.commonName)
        assertEquals("CN=VALID_CN, OU=VALID_OU, O=VALID_O, L=VALID_L, ST=VALID_ST, C=DE", name.toString())
    }

    @Test
    fun `Should parse attributes with trailing whitespace`() {
        val name = MemberX500Name.parse("O=VALID_O , L=VALID_L , C=DE , OU=VALID_OU , CN=VALID_CN , ST=VALID_ST" )
        assertEquals("VALID_O", name.organization)
        assertEquals("VALID_L", name.locality)
        assertEquals("DE", name.country)
        assertEquals("VALID_ST", name.state)
        assertEquals("VALID_OU", name.organizationUnit)
        assertEquals("VALID_CN", name.commonName)
        assertEquals("CN=VALID_CN, OU=VALID_OU, O=VALID_O, L=VALID_L, ST=VALID_ST, C=DE", name.toString())
    }

    @Test
    fun `Should parse value with equals sign`() {
        val name = MemberX500Name.parse("O=IN=VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        assertEquals("CN=VALID, OU=VALID, O=\"IN=VALID\", L=VALID, C=DE", name.toString())
    }

    @Test
    fun `Should parse value with dollar sign`() {
        val name = MemberX500Name.parse("O=VA\$LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        assertEquals("CN=VALID, OU=VALID, O=VA\$LID, L=VALID, C=DE", name.toString())
    }

    @Test
    fun `Should throw IllegalArgumentException for attributes with double quotation mark`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=IN\"VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
    }

    @Test
    fun `Should parse attributes with single quotation mark`() {
        val name = MemberX500Name.parse("O=VA'LID, L=VALID, C=DE, OU=VALID, CN=VALID")
        assertEquals("CN=VALID, OU=VALID, O=VA'LID, L=VALID, C=DE", name.toString())
    }

    @Test
    fun `Should throw IllegalArgumentException for attributes with backslash`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.parse("O=IN\\VALID, L=VALID, C=DE, OU=VALID, CN=VALID")
        }
    }

    @Test
    fun `Should create MemberX500Name without organizationUnit and state`() {
        val member = MemberX500Name("Service Name", "Org", "New York", "US")
        assertEquals("Service Name", member.commonName)
        assertEquals( "Org", member.organization)
        assertEquals("New York", member.locality)
        assertEquals("US", member.country)
        assertNull(member.state)
        assertNull(member.organizationUnit)
    }

    @Test
    fun `Should create MemberX500Name without commonName, organizationUnit and state`() {
        val member = MemberX500Name("Org", "New York", "US")
        assertEquals("Org", member.organization)
        assertEquals("New York", member.locality)
        assertEquals("US", member.country)
        assertNull(member.state)
        assertNull(member.commonName)
        assertNull(member.organizationUnit)
    }

    @Test
    fun `Should build from X500Principal`() {
        val principal = X500Principal("O=Bank A,L=Seattle,C=US,ST=Washington,CN=Service Name,OU=Org Unit")
        val name = MemberX500Name.build(principal)
        assertEquals("CN=Service Name, OU=Org Unit, O=Bank A, L=Seattle, ST=Washington, C=US", name.toString())
    }

    @Test
    fun `Should build from X500Principal with properly escaped X500 name string`() {
        val principal = X500Principal("O=\"Bank+A\", CN=\"Service<>Name\", L=\"New=York\", C=US")
        val name = MemberX500Name.build(principal)
        assertEquals("CN=\"Service<>Name\", O=\"Bank+A\", L=\"New=York\", C=US", name.toString())
    }

    @Test
    fun `Two names constructed from same attributes but in different order must be equal`() {
        val name1 = MemberX500Name.parse("O=Bank A,L=Seattle,C=US,ST=Washington,CN=Service Name,OU=Org Unit")
        val name2 = MemberX500Name.parse("L=Seattle, CN=Service Name, O=Bank A,C=US,ST=Washington,OU=Org Unit")
        assertEquals(name1.hashCode(), name2.hashCode())
        assertEquals(name1, name2)
    }

    @Test
    fun `Two names constructed from same required attributes but in different order must be equal`() {
        val name1 = MemberX500Name.parse("O=Bank A,L=Seattle,C=US")
        val name2 = MemberX500Name.parse("L=Seattle, O=Bank A, C=US")
        assertEquals(name1.hashCode(), name2.hashCode())
        assertEquals(name1, name2)
    }

    @Test
    fun `Equality with null should return false`() {
        val name = MemberX500Name.parse("L=Seattle, O=Bank A, C=US")
        assertFalse(name.equals(null))
    }

    @Test
    fun `Equality with different type should return false`() {
        val name = MemberX500Name.parse("L=Seattle, O=Bank A, C=US")
        assertFalse(name.equals("Hello World!"))
    }

    @Test
    fun `Should build attributes map from X500 name string`() {
        val map = MemberX500Name.toAttributesMap(
            "L=Seattle, CN=Service Name, O=Bank A, C=US, ST=Washington, OU=Org Unit"
        )
        assertEquals(6, map.size)
        assertTrue(map["CN"] == "Service Name")
        assertTrue(map["OU"] == "Org Unit")
        assertTrue(map["O"] == "Bank A")
        assertTrue(map["L"] == "Seattle")
        assertTrue(map["ST"] == "Washington")
        assertTrue(map["C"] == "US")
    }

    @Test
    fun `Should throw IllegalArgumentsException when build attributes map from string with unsupported attribute`() {
        assertFailsWith<IllegalArgumentException> {
            MemberX500Name.toAttributesMap(
                "L=Seattle, CN=Service Name, O=Bank A, C=US, ST=Washington, GIVENNAME=me, OU=Org Unit"
            )
        }
    }

    @Test
    fun `should give the correct order`() {
        val names = listOf(
            MemberX500Name.parse("CN=AAA, OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, OU=DDD, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, O=AA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=AAA, OU=CCC, O=ABA, L=P, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=B, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=A, C=GB"),
        )

        val sortedNames = names.sorted()

        assertThat(sortedNames).containsExactly(
            MemberX500Name.parse("OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=AAA, OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=AAA, OU=CCC, O=ABA, L=P, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, O=AA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=A, C=GB"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=A, C=US"),
            MemberX500Name.parse("CN=BBB, OU=CCC, O=ABA, L=ERR, ST=B, C=US"),
            MemberX500Name.parse("CN=BBB, OU=DDD, O=ABA, L=ERR, ST=A, C=US"),
        )
    }
}
