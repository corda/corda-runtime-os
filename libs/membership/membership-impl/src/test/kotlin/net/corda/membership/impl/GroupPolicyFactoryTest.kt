package net.corda.membership.impl

import net.corda.membership.impl.TestGroupPolicies.Companion.emptyString
import net.corda.membership.impl.TestGroupPolicies.Companion.fullGroupPolicy
import net.corda.membership.impl.TestGroupPolicies.Companion.invalidFormatGroupPolicy
import net.corda.membership.impl.TestGroupPolicies.Companion.whitespaceString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GroupPolicyFactoryImpl]
 */
class GroupPolicyFactoryTest {

    private lateinit var groupPolicyFactory: GroupPolicyFactoryImpl

    @BeforeEach
    fun setUp() {
        groupPolicyFactory = GroupPolicyFactoryImpl()
    }

    /**
     * Test that an empty string passed in for the group policy doesn't throw an exception but instead results in an
     * empty map.
     */
    @Test
    fun `Empty string as group policy returns empty group policy`() {
        val result = groupPolicyFactory.createGroupPolicy(emptyString)
        assertEquals(0, result.size)
    }

    /**
     * Test that a string of whitespace passed in for the group policy doesn't throw an exception but instead results
     * in an empty map.
     */
    @Test
    fun `Whitespace string as group policy returns empty group policy`() {
        val result = groupPolicyFactory.createGroupPolicy(whitespaceString)
        assertEquals(0, result.size)
    }

    /**
     * Test that invalid JSON passed in for the group policy doesn't throw an exception but instead results
     * in an empty map.
     */
    @Test
    fun `Invalid format group policy results in empty group policy map`() {
        val result = groupPolicyFactory.createGroupPolicy(invalidFormatGroupPolicy)
        assertEquals(0, result.size)
    }

    /**
     * Parse a full group policy sample and check the public interface properties are as expected.
     */
    @Test
    fun `Parse group policy - verify interface properties`() {
        val result = groupPolicyFactory.createGroupPolicy(fullGroupPolicy)
        assertEquals("ABC123", result.groupId)
    }

    /**
     * Parse a full group policy sample and check that the internal map values are as expected.
     * This test only verifies two levels rather than every single value.
     */
    @Test
    fun `Parse group policy - verify internal map`() {
        val result = groupPolicyFactory.createGroupPolicy(fullGroupPolicy)

        // Top level properties
        assertEquals(1, result["fileFormatVersion"])
        assertEquals("ABC123", result["groupId"])
        assertEquals("net.corda.v5.mgm.MGMRegistrationProtocolFactory", result["registrationProtocolFactory"])
        assertEquals("net.corda.v5.mgm.MGMSynchronisationProtocolFactory", result["synchronisationProtocolFactory"])
        assertTrue(result["protocolParameters"] is Map<*, *>)
        assertTrue(result["staticMemberTemplate"] is List<*>)

        // Protocol parameters
        val protocolParameters = result["protocolParameters"] as Map<*, *>
        assertEquals(7, protocolParameters.size)
        assertEquals("Standard", protocolParameters["identityPKI"])
        assertEquals("Combined", protocolParameters["identityKeyPolicy"])

        assertTrue(protocolParameters["identityTrustStore"] is List<*>)
        assertEquals(2, (protocolParameters["identityTrustStore"] as List<*>).size)

        assertTrue(protocolParameters["tlsTrustStore"] is List<*>)
        assertEquals(3, (protocolParameters["tlsTrustStore"] as List<*>).size)

        assertTrue(protocolParameters["mgmInfo"] is Map<*, *>)
        assertEquals(9, (protocolParameters["mgmInfo"] as Map<*, *>).size)

        assertTrue(protocolParameters["cipherSuite"] is Map<*, *>)
        assertEquals(6, (protocolParameters["cipherSuite"] as Map<*, *>).size)

        assertTrue(protocolParameters["roles"] is Map<*, *>)
        assertEquals(2, (protocolParameters["roles"] as Map<*, *>).size)

        // Static member template
        val staticMemberTemplate = result["staticMemberTemplate"] as List<*>
        assertEquals(3, staticMemberTemplate.size)
    }
}

private class TestGroupPolicies {
    companion object {
        const val emptyString = ""
        const val whitespaceString = "                  "
        const val invalidFormatGroupPolicy = "{{[{[{[{[{[{[ \"groupId\": \"ABC123\" }"
        const val fullGroupPolicy = """
{
  "fileFormatVersion": 1,
  "groupId": "ABC123",
  "registrationProtocolFactory": "net.corda.v5.mgm.MGMRegistrationProtocolFactory",
  "synchronisationProtocolFactory": "net.corda.v5.mgm.MGMSynchronisationProtocolFactory",
  "protocolParameters": {
    "identityTrustStore": [
      "-----BEGIN CERTIFICATE-----\nMIICCDCJDBZFSiI=\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFPDCzIlifT20M\n-----END CERTIFICATE-----"
    ],
    "tlsTrustStore": [
      "-----BEGIN CERTIFICATE-----\nMIIDxTCCE6N36B9K\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIDdTCCKSZp4A==\n-----END CERTIFICATE-----",
      "-----BEGIN CERTIFICATE-----\nMIIFPDCCIlifT20M\n-----END CERTIFICATE-----"
    ],
    "mgmInfo": {
      "x500Name": "C=GB, L=London, O=Corda Network, OU=MGM, CN=Corda Network MGM",
      "sessionKey": "-----BEGIN PUBLIC KEY-----\nMFkwEwYHK+B3YGgcIALw==\n-----END PUBLIC KEY-----\n",
      "certificate": [
        "-----BEGIN CERTIFICATE-----\nMIICxjCCRG11cu1\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIIB/TCCDJOIjhJ\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIICCDCCDZFSiI=\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIIFPDCClifT20M\n-----END CERTIFICATE-----"
      ],
      "ecdhKey": "-----BEGIN PUBLIC KEY-----\nMCowBQYDH8Tc=\n-----END PUBLIC KEY-----\n",
      "keys": [
        "-----BEGIN PUBLIC KEY-----\nMFkwEwYHgcIALw==\n-----END PUBLIC KEY-----\n"
      ],
      "endpoints": [
        {
          "url": "https://mgm.corda5.r3.com:10000",
          "protocolVersion": 1
        },
        {
          "url": "https://mgm-dr.corda5.r3.com:10000",
          "protocolVersion": 1
        }
      ],
      "platformVersion": 1,
      "softwareVersion": "5.0.0",
      "serial": 1
    },
    "identityPKI": "Standard",
    "identityKeyPolicy": "Combined",
    "cipherSuite": {
      "corda.provider": "default",
      "corda.signature.provider": "default",
      "corda.signature.default": "ECDSA_SECP256K1_SHA256",
      "corda.signature.FRESH_KEYS": "ECDSA_SECP256K1_SHA256",
      "corda.digest.default": "SHA256",
      "corda.cryptoservice.provider": "default"
    },
    "roles" : {
      "default" : {
        "validator" : "net.corda.v5.mgm.DefaultMemberInfoValidator",
        "requiredMemberInfo" : [
        ],
        "optionalMemberInfo" : [
        ]
      },
      "notary" : {
        "validator" : "net.corda.v5.mgm.NotaryMemberInfoValidator",
        "requiredMemberInfo" : [
          "notaryServiceParty"
        ],
        "optionalMemberInfo" : [
        ]
      }
    }
  },
  "staticMemberTemplate": [
    {
      "x500Name": "C=GB, L=London, O=Alice", // party name
      "keyAlias": "alice-alias", // current party identity key
      "rotatedKeyAlias-1": "alice-historic-alias-1", // Used to force old keys no longer in use (i.e. rotated key)
      "memberStatus": "ACTIVE", // Member status usually set by the MGM
      "endpointUrl-1": "https://alice.corda5.r3.com:10000", // Endpoint url & protocol. Iterable.
      "endpointProtocol-1": 1                                //(uses incrementing postfix for repeating elements)
    },
    {
      "x500Name": "C=GB, L=London, O=Bob",
      "keyAlias": "bob-alias",
      "rotatedKeyAlias-1": "bob-historic-alias-1",
      "rotatedKeyAlias-2": "bob-historic-alias-2",
      "memberStatus": "ACTIVE",
      "endpointUrl-1": "https://bob.corda5.r3.com:10000",
      "endpointProtocol-1": 1
    },
    {
      "x500Name": "C=GB, L=London, O=Charlie",
      "keyAlias": "charlie-alias",
      "memberStatus": "SUSPENDED",
      "endpointUrl-1": "https://charlie.corda5.r3.com:10000",
      "endpointProtocol-1": 1,
      "endpointUrl-2": "https://charlie-dr.corda5.r3.com:10001",
      "endpointProtocol-2": 1
    }
  ]
}
        """
    }
}