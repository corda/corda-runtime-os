package net.corda.membership.lib.impl.grouppolicy

import net.corda.membership.lib.grouppolicy.InteropGroupPolicyReader
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory

//TODO CORE-10445 Add a new kafka topic for this
@Component(service = [InteropGroupPolicyReader::class])
class InteropGroupPolicyReaderImpl @Activate constructor(): InteropGroupPolicyReader {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    // Temporary service returns the same group policy file.
    // In order to avoid breaking other tests like
    // "MemberProcessorIntegrationTest Get group policy fails for unknown holding identity"
    // which expect to receive a null, this method  returns the group policy
    // only if HoldingIdentity's group contains "interop" (case-insensitive) in its name.
    override fun getGroupPolicy(holdingIdentity: HoldingIdentity): String? {
        logger.info("Searching for interoperability group for $holdingIdentity")
        return if(holdingIdentity.groupId.contains("interop", true))
         """{
  "fileFormatVersion": 1,
  "groupId": "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08",
  "registrationProtocol": "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
  "protocolParameters": {
    "sessionKeyPolicy": "Combined",
    "staticNetwork": {
      "members": []
    }
  },
  "p2pParameters": {
    "sessionTrustRoots": [],
    "tlsTrustRoots": [
      "-----BEGIN CERTIFICATE-----\r\nMIIFHjCCBAagAwIBAgISAy1ybKW9u73QQxLAktLHTEQQMA0GCSqGSIb3DQEBCwUA\r\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\r\nEwJSMzAeFw0yMjA1MTExNTAxMDZaFw0yMjA4MDkxNTAxMDVaMBExDzANBgNVBAMT\r\nBnIzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMOna/+\r\nr0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmiVzKn\r\n6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoCDnKg\r\nzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEHsxMK\r\n+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lFZ2ro\r\nWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gzMHdf\r\nNwDftNqTuMkCAwEAAaOCAk0wggJJMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAU\r\nBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUtd2w\r\n7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+vnYsU\r\nwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5sZW5j\r\nci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wHQYDVR0R\r\nBBYwFIIGcjMuY29tggp3d3cucjMuY29tMEwGA1UdIARFMEMwCAYGZ4EMAQIBMDcG\r\nCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5\r\ncHQub3JnMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHYAKXm+8J45OSHwVnOfY6V3\r\n5b5XfZxgCvj5TV0mXCVdx4QAAAGAs9o+JQAABAMARzBFAiEAi07Xbw6nqHBtGQzN\r\nLXbCPx68E2xYa9M/ytztzJb96IYCIAiIc9y7u2H510F8AQ1zon7wDQjaTTvL3Ezl\r\nJBgFK02aAHYAQcjKsd8iRkoQxqE6CUKHXk4xixsD6+tLx2jwkGKWBvYAAAGAs9o+\r\nZwAABAMARzBFAiEAyO7PeW40ocwt+QqSMZAJHKRe7Ip1kYkjUhabhVQD0CoCIBpD\r\nqJEJd3UlGIUyxJ44i72xQ6kvn5adfnmJE5Jh8YwPMA0GCSqGSIb3DQEBCwUAA4IB\r\nAQBRENd2mg7C73zwxAduIDcYaQ+bKaM9+edHBC+h7cDSACdQ1J+AKruWWYOfQJXG\r\nQvudeDU2W7+kUC/0fq0Ui9cGCQBY+EacFN6261z2jVLtdGwJWRe2pYwIVOdknFet\r\nMY31Fqih/HToiaX1Fz0qkN0TrdLBsMIZEx3XAiMHbJH4AOrr+V2FpV6GIAZ1A68I\r\nFkR4W6zPnI7cwjpeLnO6x1A92y5txtNBeBu0DDnbt695J8BVZeJBei0gIVe3y1Xf\r\n2xPJfCYMCpysD6ADGOmMrahZ4ANfDck27hIDw8GXYDBp8XP7teM7r/OVRJW5MJUK\r\nxcTyC7ANrJ7GGChxJZUaq0Qu\r\n-----END CERTIFICATE-----\r\n",
      "-----BEGIN CERTIFICATE-----\r\nMIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\r\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\r\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\r\nWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\r\nRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\r\nAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\r\nR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\r\nsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\r\nNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\r\nZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\r\n/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\r\nAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\r\nAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\r\nFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\r\nAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\r\nOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\r\ngt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\r\nPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\r\nikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\r\nCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\r\nlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\r\navAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\r\nyJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\r\nyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\r\nhCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\r\nHlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\r\nMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\r\nnLRbwHOoq7hHwg==\r\n-----END CERTIFICATE-----\r\n",
      "-----BEGIN CERTIFICATE-----\r\nMIIFYDCCBEigAwIBAgIQQAF3ITfU6UK47naqPGQKtzANBgkqhkiG9w0BAQsFADA/\r\nMSQwIgYDVQQKExtEaWdpdGFsIFNpZ25hdHVyZSBUcnVzdCBDby4xFzAVBgNVBAMT\r\nDkRTVCBSb290IENBIFgzMB4XDTIxMDEyMDE5MTQwM1oXDTI0MDkzMDE4MTQwM1ow\r\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\r\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwggIiMA0GCSqGSIb3DQEB\r\nAQUAA4ICDwAwggIKAoICAQCt6CRz9BQ385ueK1coHIe+3LffOJCMbjzmV6B493XC\r\nov71am72AE8o295ohmxEk7axY/0UEmu/H9LqMZshftEzPLpI9d1537O4/xLxIZpL\r\nwYqGcWlKZmZsj348cL+tKSIG8+TA5oCu4kuPt5l+lAOf00eXfJlII1PoOK5PCm+D\r\nLtFJV4yAdLbaL9A4jXsDcCEbdfIwPPqPrt3aY6vrFk/CjhFLfs8L6P+1dy70sntK\r\n4EwSJQxwjQMpoOFTJOwT2e4ZvxCzSow/iaNhUd6shweU9GNx7C7ib1uYgeGJXDR5\r\nbHbvO5BieebbpJovJsXQEOEO3tkQjhb7t/eo98flAgeYjzYIlefiN5YNNnWe+w5y\r\nsR2bvAP5SQXYgd0FtCrWQemsAXaVCg/Y39W9Eh81LygXbNKYwagJZHduRze6zqxZ\r\nXmidf3LWicUGQSk+WT7dJvUkyRGnWqNMQB9GoZm1pzpRboY7nn1ypxIFeFntPlF4\r\nFQsDj43QLwWyPntKHEtzBRL8xurgUBN8Q5N0s8p0544fAQjQMNRbcTa0B7rBMDBc\r\nSLeCO5imfWCKoqMpgsy6vYMEG6KDA0Gh1gXxG8K28Kh8hjtGqEgqiNx2mna/H2ql\r\nPRmP6zjzZN7IKw0KKP/32+IVQtQi0Cdd4Xn+GOdwiK1O5tmLOsbdJ1Fu/7xk9TND\r\nTwIDAQABo4IBRjCCAUIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYw\r\nSwYIKwYBBQUHAQEEPzA9MDsGCCsGAQUFBzAChi9odHRwOi8vYXBwcy5pZGVudHJ1\r\nc3QuY29tL3Jvb3RzL2RzdHJvb3RjYXgzLnA3YzAfBgNVHSMEGDAWgBTEp7Gkeyxx\r\n+tvhS5B1/8QVYIWJEDBUBgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEB\r\nATAwMC4GCCsGAQUFBwIBFiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQu\r\nb3JnMDwGA1UdHwQ1MDMwMaAvoC2GK2h0dHA6Ly9jcmwuaWRlbnRydXN0LmNvbS9E\r\nU1RST09UQ0FYM0NSTC5jcmwwHQYDVR0OBBYEFHm0WeZ7tuXkAXOACIjIGlj26Ztu\r\nMA0GCSqGSIb3DQEBCwUAA4IBAQAKcwBslm7/DlLQrt2M51oGrS+o44+/yQoDFVDC\r\n5WxCu2+b9LRPwkSICHXM6webFGJueN7sJ7o5XPWioW5WlHAQU7G75K/QosMrAdSW\r\n9MUgNTP52GE24HGNtLi1qoJFlcDyqSMo59ahy2cI2qBDLKobkx/J3vWraV0T9VuG\r\nWCLKTVXkcGdtwlfFRjlBz4pYg1htmf5X6DYO8A4jqv2Il9DjXA6USbW1FzXSLr9O\r\nhe8Y4IWS6wY7bCkjCWDcRQJMEhg76fsO3txE+FiYruq9RUWhiF1myv4Q6W+CyBFC\r\nDfvp7OOGAN6dEOM4+qR9sdjoSYKEBpsr6GtPAQw4dy753ec5\r\n-----END CERTIFICATE-----\r\n"
    ],
    "sessionPki": "Standard",
    "tlsPki": "Standard",
    "tlsVersion": "1.3",
    "protocolMode": "Authenticated_Encryption",
    "tlsType": "OneWay"
  },
  "cipherSuite": {}
}"""
        else null
    }
}