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
      "-----BEGIN CERTIFICATE-----\nMIICCDCCAa2gAwIBAgIUOiuhZIfc2uSlfhmX6Ec6wq15KRcwCgYIKoZIzj0EAwIw\nUTELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxvbmRvbjEWMBQGA1UECgwNQ29yZGEg\nTmV0d29yazEZMBcGA1UEAwwQQ29yZGEgTmV0d29yayBDQTAeFw0yMTA4MTMxMzA5\nMTNaFw00MTA4MDgxMzA5MTNaMFExCzAJBgNVBAYTAkdCMQ8wDQYDVQQHDAZMb25k\nb24xFjAUBgNVBAoMDUNvcmRhIE5ldHdvcmsxGTAXBgNVBAMMEENvcmRhIE5ldHdv\ncmsgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATPrnqSTA1n6o0GND9hh5G9\nuDyEt0hsHPkSSUpKUbRdDZ5pEUJX/YwdW3h9t8zbPeHh1BWULSLDICihP9m+O4G1\no2MwYTAdBgNVHQ4EFgQUydmYovRyjosYMszE+zYBH3gQhDgwHwYDVR0jBBgwFoAU\nydmYovRyjosYMszE+zYBH3gQhDgwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8E\nBAMCAYYwCgYIKoZIzj0EAwIDSQAwRgIhAL5vPaN71gp+BduXnCKybZTwsDSAzJ1V\n/T4ocLq+7uGyAiEAmL05MDkSam9/+5xjfBJ8ax7ZGLMcL8kgrnwJDBZFSiI=\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFPDCCAyQCCQCtQ41FuP2jNTANBgkqhkiG9w0BAQ0FADBgMQswCQYDVQQGEwJH\nQjEPMA0GA1UEBwwGTG9uZG9uMRYwFAYDVQQKDA1Db3JkYSBOZXR3b3JrMQwwCgYD\nVQQLDANNR00xGjAYBgNVBAMMEUNvcmRhIE5ldHdvcmsgTUdNMB4XDTIxMTAyMDEy\nMDMzN1oXDTMxMTAxODEyMDMzN1owYDELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxv\nbmRvbjEWMBQGA1UECgwNQ29yZGEgTmV0d29yazEMMAoGA1UECwwDTUdNMRowGAYD\nVQQDDBFDb3JkYSBOZXR3b3JrIE1HTTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC\nAgoCggIBAKS+OpePwy9nAMMziCFhbge0dmoWQ1I7WaxjxgiY6sXzLbtmAmzGtWkH\nxZfqBuguM8Oxpw9j6EVqTy8PJosVhMSv/kEIU8z9dUbHnAgh1pxhfD3+6AAdYmF2\nSpUEQ+OOqygsnNuPW70PqUmlk3qkVEVg4oxVSUdJ8FVkGOCPqA/9Y3ShgjwHxWXa\nmDi6ezc49swUd00qIBkVm6c3CWL+sXN8tYbRgIK00Tnbgj6bImeYXjaasbF9Rfpy\n13ruvYUQUxqCLheoBh+7Wo96/tJDcb4Mjbu7XYQcOz43DuB0VT5FTmn8/QOYhMKh\n8SCAKOJRP/JXouOM6L9PwSRtkx+t+59eoGPU2W0LbG6aViLX/oW2Mv/h8JTH76Pc\nGYwN9P0fgUG8aWjWubWB8Aw3D4eb7iOb2eQJTPgh5Faib9HRpgLp4hcu3cYox73G\ns0HGa2547vaNLyRZKnTQXNRyyb7401Fm3S8gDdFFKsx7v0WyXkzvKqLWgONRIWoM\nFDxpGAEo2OXgdGMA2sToZh1191iWixtlipSoPyUSYKsdNSfrmEu8TqRsIODQKp/3\nP5W4SOdoo8TeUna51JIpeT8C7u9iG6szXb1AfS6nfQswkAeNQLbNp8LJVgiPlcsI\nh59Tnfi9PMSsdaDBdbrpaI52NIXZEPy/FJoh4p1vYbiOHmhT8bjvAgMBAAEwDQYJ\nKoZIhvcNAQENBQADggIBAAdZFAwewESYIDSRfLcxcEhUOZyQ4w1oO9BKXBBaBL7S\naRhjUSjm59q6c439saq1tvnrbayirTt7qH1S7B+pygRscHC87NNNXusvBS26oZm/\ngoTdvlw5Tlxu1d8s7HLaTruhgEeM7agi0cAuUHMKY5pvzB3FgYHs7PoMU/2+2xh5\n1ymDWtKTeoeHGwU5PTeCWWygpW6iiYIz4Vc35WwfgQjMoUrAOx+/hEPVYk4ewcfu\nT/t2mqPZ8Zh1fzMqmBlIqUBS44a0s+ajqBb8KAeZ9Xxwp/4l+MnR+5jxbZ+5zNSy\nRr8jHkaGzgTSCQDopPcc3YnJb5c02ru+YP4XaavTs5OU0+ZvXLaX3tOLz8MAS/RV\no2AuKxyLxc2BZNa12D0z84KdtrYtZrRiD4WTPYuk173AfVVpbhte8XDKbYOHe6Ox\nFSrZq0rt9kVNcVRiD9AAWZKKqP1xN0532tAP3b/oPfvR9s5L5S5neqHB4+bQcrKq\nkHJVqelJQ00aHa8x1TuUm+i91vzIrK9my0esoInYddCv/6phGr8TavelpOyJR0TO\nv+2HQy1jESWHs8zw0dU+DTe8a6U0iL2z5Rt0/hCuXG0UY4GjWYZ2OIzy57OLj9T9\nZm/9tcEdp4YYHyeRtG3V0J20FEGD1x30bUwrdBT5buC9JjHx3DKyHI+zIlifT20M\n-----END CERTIFICATE-----"
    ],
    "tlsTrustStore": [
      "-----BEGIN CERTIFICATE-----\nMIIDxTCCAq2gAwIBAgIQAqxcJmoLQJuPC3nyrkYldzANBgkqhkiG9w0BAQUFADBs\nMQswCQYDVQQGEwJVUzEVMBMGA1UEChMMRGlnaUNlcnQgSW5jMRkwFwYDVQQLExB3\nd3cuZGlnaWNlcnQuY29tMSswKQYDVQQDEyJEaWdpQ2VydCBIaWdoIEFzc3VyYW5j\nZSBFViBSb290IENBMB4XDTA2MTExMDAwMDAwMFoXDTMxMTExMDAwMDAwMFowbDEL\nMAkGA1UEBhMCVVMxFTATBgNVBAoTDERpZ2lDZXJ0IEluYzEZMBcGA1UECxMQd3d3\nLmRpZ2ljZXJ0LmNvbTErMCkGA1UEAxMiRGlnaUNlcnQgSGlnaCBBc3N1cmFuY2Ug\nRVYgUm9vdCBDQTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMbM5XPm\n+9S75S0tMqbf5YE/yc0lSbZxKsPVlDRnogocsF9ppkCxxLeyj9CYpKlBWTrT3JTW\nPNt0OKRKzE0lgvdKpVMSOO7zSW1xkX5jtqumX8OkhPhPYlG++MXs2ziS4wblCJEM\nxChBVfvLWokVfnHoNb9Ncgk9vjo4UFt3MRuNs8ckRZqnrG0AFFoEt7oT61EKmEFB\nIk5lYYeBQVCmeVyJ3hlKV9Uu5l0cUyx+mM0aBhakaHPQNAQTXKFx01p8VdteZOE3\nhzBWBOURtCmAEvF5OYiiAhF8J2a3iLd48soKqDirCmTCv2ZdlYTBoSUeh10aUAsg\nEsxBu24LUTi4S8sCAwEAAaNjMGEwDgYDVR0PAQH/BAQDAgGGMA8GA1UdEwEB/wQF\nMAMBAf8wHQYDVR0OBBYEFLE+w2kD+L9HAdSYJhoIAu9jZCvDMB8GA1UdIwQYMBaA\nFLE+w2kD+L9HAdSYJhoIAu9jZCvDMA0GCSqGSIb3DQEBBQUAA4IBAQAcGgaX3Nec\nnzyIZgYIVyHbIUf4KmeqvxgydkAQV8GK83rZEWWONfqe/EW1ntlMMUu4kehDLI6z\neM7b41N5cdblIZQB2lWHmiRk9opmzN6cN82oNLFpmyPInngiK3BD41VHMWEZ71jF\nhS9OMPagMRYjyOfiZRYzy78aG6A9+MpeizGLYAiJLQwGXFK3xPkKmNEVX58Svnw2\nYzi9RKR/5CYrCsSXaQ3pjOLAEFe4yHYSkVXySGnYvCoCWw9E1CAx2/S6cCZdkGCe\nvEsXCS+0yx5DaMkHJ8HSXPfqIbloEpw8nL+e/IBcm2PN7EeqJSdnoDfzAIJ9VNep\n+OkuE6N36B9K\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIDdTCCAl2gAwIBAgILBAAAAAABFUtaw5QwDQYJKoZIhvcNAQEFBQAwVzELMAkG\nA1UEBhMCQkUxGTAXBgNVBAoTEEdsb2JhbFNpZ24gbnYtc2ExEDAOBgNVBAsTB1Jv\nb3QgQ0ExGzAZBgNVBAMTEkdsb2JhbFNpZ24gUm9vdCBDQTAeFw05ODA5MDExMjAw\nMDBaFw0yODAxMjgxMjAwMDBaMFcxCzAJBgNVBAYTAkJFMRkwFwYDVQQKExBHbG9i\nYWxTaWduIG52LXNhMRAwDgYDVQQLEwdSb290IENBMRswGQYDVQQDExJHbG9iYWxT\naWduIFJvb3QgQ0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDaDuaZ\njc6j40+Kfvvxi4Mla+pIH/EqsLmVEQS98GPR4mdmzxzdzxtIK+6NiY6arymAZavp\nxy0Sy6scTHAHoT0KMM0VjU/43dSMUBUc71DuxC73/OlS8pF94G3VNTCOXkNz8kHp\n1Wrjsok6Vjk4bwY8iGlbKk3Fp1S4bInMm/k8yuX9ifUSPJJ4ltbcdG6TRGHRjcdG\nsnUOhugZitVtbNV4FpWi6cgKOOvyJBNPc1STE4U6G7weNLWLBYy5d4ux2x8gkasJ\nU26Qzns3dLlwR5EiUWMWea6xrkEmCMgZK9FGqkjWZCrXgzT/LCrBbBlDSgeF59N8\n9iFo7+ryUp9/k5DPAgMBAAGjQjBAMA4GA1UdDwEB/wQEAwIBBjAPBgNVHRMBAf8E\nBTADAQH/MB0GA1UdDgQWBBRge2YaRQ2XyolQL30EzTSo//z9SzANBgkqhkiG9w0B\nAQUFAAOCAQEA1nPnfE920I2/7LqivjTFKDK1fPxsnCwrvQmeU79rXqoRSLblCKOz\nyj1hTdNGCbM+w6DjY1Ub8rrvrTnhQ7k4o+YviiY776BQVvnGCv04zcQLcFGUl5gE\n38NflNUVyRRBnMRddWQVDf9VMOyGj/8N7yy5Y0b2qvzfvGn9LhJIZJrglfCm7ymP\nAbEVtQwdpf5pLGkkeB6zpxxxYu7KyJesF12KwvhHhm4qxFYxldBniYUr+WymXUad\nDKqC5JlR3XC321Y9YeRq4VzW9v493kHMB65jUr9TU/Qr6cf9tveCX4XSQRjbgbME\nHMUfpIBvFSDJ3gyICh3WZlXi/EjJKSZp4A==\n-----END CERTIFICATE-----",
      "-----BEGIN CERTIFICATE-----\nMIIFPDCCAyQCCQCtQ41FuP2jNTANBgkqhkiG9w0BAQ0FADBgMQswCQYDVQQGEwJH\nQjEPMA0GA1UEBwwGTG9uZG9uMRYwFAYDVQQKDA1Db3JkYSBOZXR3b3JrMQwwCgYD\nVQQLDANNR00xGjAYBgNVBAMMEUNvcmRhIE5ldHdvcmsgTUdNMB4XDTIxMTAyMDEy\nMDMzN1oXDTMxMTAxODEyMDMzN1owYDELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxv\nbmRvbjEWMBQGA1UECgwNQ29yZGEgTmV0d29yazEMMAoGA1UECwwDTUdNMRowGAYD\nVQQDDBFDb3JkYSBOZXR3b3JrIE1HTTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC\nAgoCggIBAKS+OpePwy9nAMMziCFhbge0dmoWQ1I7WaxjxgiY6sXzLbtmAmzGtWkH\nxZfqBuguM8Oxpw9j6EVqTy8PJosVhMSv/kEIU8z9dUbHnAgh1pxhfD3+6AAdYmF2\nSpUEQ+OOqygsnNuPW70PqUmlk3qkVEVg4oxVSUdJ8FVkGOCPqA/9Y3ShgjwHxWXa\nmDi6ezc49swUd00qIBkVm6c3CWL+sXN8tYbRgIK00Tnbgj6bImeYXjaasbF9Rfpy\n13ruvYUQUxqCLheoBh+7Wo96/tJDcb4Mjbu7XYQcOz43DuB0VT5FTmn8/QOYhMKh\n8SCAKOJRP/JXouOM6L9PwSRtkx+t+59eoGPU2W0LbG6aViLX/oW2Mv/h8JTH76Pc\nGYwN9P0fgUG8aWjWubWB8Aw3D4eb7iOb2eQJTPgh5Faib9HRpgLp4hcu3cYox73G\ns0HGa2547vaNLyRZKnTQXNRyyb7401Fm3S8gDdFFKsx7v0WyXkzvKqLWgONRIWoM\nFDxpGAEo2OXgdGMA2sToZh1191iWixtlipSoPyUSYKsdNSfrmEu8TqRsIODQKp/3\nP5W4SOdoo8TeUna51JIpeT8C7u9iG6szXb1AfS6nfQswkAeNQLbNp8LJVgiPlcsI\nh59Tnfi9PMSsdaDBdbrpaI52NIXZEPy/FJoh4p1vYbiOHmhT8bjvAgMBAAEwDQYJ\nKoZIhvcNAQENBQADggIBAAdZFAwewESYIDSRfLcxcEhUOZyQ4w1oO9BKXBBaBL7S\naRhjUSjm59q6c439saq1tvnrbayirTt7qH1S7B+pygRscHC87NNNXusvBS26oZm/\ngoTdvlw5Tlxu1d8s7HLaTruhgEeM7agi0cAuUHMKY5pvzB3FgYHs7PoMU/2+2xh5\n1ymDWtKTeoeHGwU5PTeCWWygpW6iiYIz4Vc35WwfgQjMoUrAOx+/hEPVYk4ewcfu\nT/t2mqPZ8Zh1fzMqmBlIqUBS44a0s+ajqBb8KAeZ9Xxwp/4l+MnR+5jxbZ+5zNSy\nRr8jHkaGzgTSCQDopPcc3YnJb5c02ru+YP4XaavTs5OU0+ZvXLaX3tOLz8MAS/RV\no2AuKxyLxc2BZNa12D0z84KdtrYtZrRiD4WTPYuk173AfVVpbhte8XDKbYOHe6Ox\nFSrZq0rt9kVNcVRiD9AAWZKKqP1xN0532tAP3b/oPfvR9s5L5S5neqHB4+bQcrKq\nkHJVqelJQ00aHa8x1TuUm+i91vzIrK9my0esoInYddCv/6phGr8TavelpOyJR0TO\nv+2HQy1jESWHs8zw0dU+DTe8a6U0iL2z5Rt0/hCuXG0UY4GjWYZ2OIzy57OLj9T9\nZm/9tcEdp4YYHyeRtG3V0J20FEGD1x30bUwrdBT5buC9JjHx3DKyHI+zIlifT20M\n-----END CERTIFICATE-----"
    ],
    "mgmInfo": {
      "x500Name": "C=GB, L=London, O=Corda Network, OU=MGM, CN=Corda Network MGM",
      "sessionKey": "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECyUrI6a25feoVKvjmD24C2SUzRdz\npKly/dRMecKmPak1jHHb0C63+miTBrrVr8lzdj5eFpEIKku+B3YGgcIALw==\n-----END PUBLIC KEY-----\n",
      "certificate": [
        "-----BEGIN CERTIFICATE-----\nMIICxjCCAmygAwIBAgICEAAwCgYIKoZIzj0EAwIwVjELMAkGA1UEBhMCR0IxDzAN\nBgNVBAcMBkxvbmRvbjEWMBQGA1UECgwNQ29yZGEgTmV0d29yazEeMBwGA1UEAwwV\nQ29yZGEgSW50ZXJtZWRpYXRlIENBMB4XDTIxMDgxMzEzMDkxM1oXDTMxMDgxMTEz\nMDkxM1owYDELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxvbmRvbjEWMBQGA1UECgwN\nQ29yZGEgTmV0d29yazEMMAoGA1UECwwDTUdNMRowGAYDVQQDDBFDb3JkYSBOZXR3\nb3JrIE1HTTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABAslKyOmtuX3qFSr45g9\nuAtklM0Xc6Spcv3UTHnCpj2pNYxx29Aut/pokwa61a/Jc3Y+XhaRCCpLvgd2BoHC\nAC+jggEeMIIBGjAJBgNVHRMEAjAAMBEGCWCGSAGG+EIBAQQEAwIGwDAwBglghkgB\nhvhCAQ0EIxYhT3BlblNTTCBHZW5lcmF0ZWQgTUdNIENlcnRpZmljYXRlMB0GA1Ud\nDgQWBBQqLFEQt8OeWnpC3M0eFWoFEFbuozB6BgNVHSMEczBxgBTJVZG8c5rP8WxA\nq1eF8P7q5IBVw6FVpFMwUTELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxvbmRvbjEW\nMBQGA1UECgwNQ29yZGEgTmV0d29yazEZMBcGA1UEAwwQQ29yZGEgTmV0d29yayBD\nQYICEAAwDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQGCCsGAQUFBwMCBggrBgEF\nBQcDATAKBggqhkjOPQQDAgNIADBFAiB8hTsMWro9SRntVTEI6TETdEAljgmbWUBp\nEDCeQUzh/gIhALMb1w3G/MrI4fnnC2lkzbALWhu+Dui57cxctRG11cu1\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIIB/TCCAaOgAwIBAgICEAAwCgYIKoZIzj0EAwIwUTELMAkGA1UEBhMCR0IxDzAN\nBgNVBAcMBkxvbmRvbjEWMBQGA1UECgwNQ29yZGEgTmV0d29yazEZMBcGA1UEAwwQ\nQ29yZGEgTmV0d29yayBDQTAeFw0yMTA4MTMxMzA5MTNaFw0zMTA4MTExMzA5MTNa\nMFYxCzAJBgNVBAYTAkdCMQ8wDQYDVQQHDAZMb25kb24xFjAUBgNVBAoMDUNvcmRh\nIE5ldHdvcmsxHjAcBgNVBAMMFUNvcmRhIEludGVybWVkaWF0ZSBDQTBZMBMGByqG\nSM49AgEGCCqGSM49AwEHA0IABOtjK3CYesqnxRNP++b5TYwkoVrcCme6uRzzfqtU\n+wXSOYmy3Xg0ftmuLI1jb+FpWgYKUGmds2ffBz1KZtWtmw+jZjBkMB0GA1UdDgQW\nBBTJVZG8c5rP8WxAq1eF8P7q5IBVwzAfBgNVHSMEGDAWgBTJ2Zii9HKOixgyzMT7\nNgEfeBCEODASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBhjAKBggq\nhkjOPQQDAgNIADBFAiEAvZCUbi6HchxyAiVU2o/04YDiAiAEf24tzxWoQOX7dHEC\nIBHCQq0XLXoxU9bvG/yx16ZftiGyAHacsPPfWFJOIjhJ\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIICCDCCAa2gAwIBAgIUOiuhZIfc2uSlfhmX6Ec6wq15KRcwCgYIKoZIzj0EAwIw\nUTELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxvbmRvbjEWMBQGA1UECgwNQ29yZGEg\nTmV0d29yazEZMBcGA1UEAwwQQ29yZGEgTmV0d29yayBDQTAeFw0yMTA4MTMxMzA5\nMTNaFw00MTA4MDgxMzA5MTNaMFExCzAJBgNVBAYTAkdCMQ8wDQYDVQQHDAZMb25k\nb24xFjAUBgNVBAoMDUNvcmRhIE5ldHdvcmsxGTAXBgNVBAMMEENvcmRhIE5ldHdv\ncmsgQ0EwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATPrnqSTA1n6o0GND9hh5G9\nuDyEt0hsHPkSSUpKUbRdDZ5pEUJX/YwdW3h9t8zbPeHh1BWULSLDICihP9m+O4G1\no2MwYTAdBgNVHQ4EFgQUydmYovRyjosYMszE+zYBH3gQhDgwHwYDVR0jBBgwFoAU\nydmYovRyjosYMszE+zYBH3gQhDgwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8E\nBAMCAYYwCgYIKoZIzj0EAwIDSQAwRgIhAL5vPaN71gp+BduXnCKybZTwsDSAzJ1V\n/T4ocLq+7uGyAiEAmL05MDkSam9/+5xjfBJ8ax7ZGLMcL8kgrnwJDBZFSiI=\n-----END CERTIFICATE-----\n",
        "-----BEGIN CERTIFICATE-----\nMIIFPDCCAyQCCQCtQ41FuP2jNTANBgkqhkiG9w0BAQ0FADBgMQswCQYDVQQGEwJH\nQjEPMA0GA1UEBwwGTG9uZG9uMRYwFAYDVQQKDA1Db3JkYSBOZXR3b3JrMQwwCgYD\nVQQLDANNR00xGjAYBgNVBAMMEUNvcmRhIE5ldHdvcmsgTUdNMB4XDTIxMTAyMDEy\nMDMzN1oXDTMxMTAxODEyMDMzN1owYDELMAkGA1UEBhMCR0IxDzANBgNVBAcMBkxv\nbmRvbjEWMBQGA1UECgwNQ29yZGEgTmV0d29yazEMMAoGA1UECwwDTUdNMRowGAYD\nVQQDDBFDb3JkYSBOZXR3b3JrIE1HTTCCAiIwDQYJKoZIhvcNAQEBBQADggIPADCC\nAgoCggIBAKS+OpePwy9nAMMziCFhbge0dmoWQ1I7WaxjxgiY6sXzLbtmAmzGtWkH\nxZfqBuguM8Oxpw9j6EVqTy8PJosVhMSv/kEIU8z9dUbHnAgh1pxhfD3+6AAdYmF2\nSpUEQ+OOqygsnNuPW70PqUmlk3qkVEVg4oxVSUdJ8FVkGOCPqA/9Y3ShgjwHxWXa\nmDi6ezc49swUd00qIBkVm6c3CWL+sXN8tYbRgIK00Tnbgj6bImeYXjaasbF9Rfpy\n13ruvYUQUxqCLheoBh+7Wo96/tJDcb4Mjbu7XYQcOz43DuB0VT5FTmn8/QOYhMKh\n8SCAKOJRP/JXouOM6L9PwSRtkx+t+59eoGPU2W0LbG6aViLX/oW2Mv/h8JTH76Pc\nGYwN9P0fgUG8aWjWubWB8Aw3D4eb7iOb2eQJTPgh5Faib9HRpgLp4hcu3cYox73G\ns0HGa2547vaNLyRZKnTQXNRyyb7401Fm3S8gDdFFKsx7v0WyXkzvKqLWgONRIWoM\nFDxpGAEo2OXgdGMA2sToZh1191iWixtlipSoPyUSYKsdNSfrmEu8TqRsIODQKp/3\nP5W4SOdoo8TeUna51JIpeT8C7u9iG6szXb1AfS6nfQswkAeNQLbNp8LJVgiPlcsI\nh59Tnfi9PMSsdaDBdbrpaI52NIXZEPy/FJoh4p1vYbiOHmhT8bjvAgMBAAEwDQYJ\nKoZIhvcNAQENBQADggIBAAdZFAwewESYIDSRfLcxcEhUOZyQ4w1oO9BKXBBaBL7S\naRhjUSjm59q6c439saq1tvnrbayirTt7qH1S7B+pygRscHC87NNNXusvBS26oZm/\ngoTdvlw5Tlxu1d8s7HLaTruhgEeM7agi0cAuUHMKY5pvzB3FgYHs7PoMU/2+2xh5\n1ymDWtKTeoeHGwU5PTeCWWygpW6iiYIz4Vc35WwfgQjMoUrAOx+/hEPVYk4ewcfu\nT/t2mqPZ8Zh1fzMqmBlIqUBS44a0s+ajqBb8KAeZ9Xxwp/4l+MnR+5jxbZ+5zNSy\nRr8jHkaGzgTSCQDopPcc3YnJb5c02ru+YP4XaavTs5OU0+ZvXLaX3tOLz8MAS/RV\no2AuKxyLxc2BZNa12D0z84KdtrYtZrRiD4WTPYuk173AfVVpbhte8XDKbYOHe6Ox\nFSrZq0rt9kVNcVRiD9AAWZKKqP1xN0532tAP3b/oPfvR9s5L5S5neqHB4+bQcrKq\nkHJVqelJQ00aHa8x1TuUm+i91vzIrK9my0esoInYddCv/6phGr8TavelpOyJR0TO\nv+2HQy1jESWHs8zw0dU+DTe8a6U0iL2z5Rt0/hCuXG0UY4GjWYZ2OIzy57OLj9T9\nZm/9tcEdp4YYHyeRtG3V0J20FEGD1x30bUwrdBT5buC9JjHx3DKyHI+zIlifT20M\n-----END CERTIFICATE-----"
      ],
      "ecdhKey": "-----BEGIN PUBLIC KEY-----\nMCowBQYDK2VuAyEAMCnCKVm51ACdhcF1P8iPvQDtnCIq4dBriZlTggaH8Tc=\n-----END PUBLIC KEY-----\n",
      "keys": [
        "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECyUrI6a25feoVKvjmD24C2SUzRdz\npKly/dRMecKmPak1jHHb0C63+miTBrrVr8lzdj5eFpEIKku+B3YGgcIALw==\n-----END PUBLIC KEY-----\n"
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