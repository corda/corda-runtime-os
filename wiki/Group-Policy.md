A CPI is a CPB bundled with network information. This network information comes in the form of a JSON file called `GroupPolicy.json`. It may be created via the `corda-cli` tooling, or for members joining a group, it would be exported from the MGM. To understand the basic concept behind the dynamic and static network, see the  [Membership](../wiki/Membership) wiki.

# Usages

Right now, there are three different variations of the group policy file.

* A group policy file needed to create an MGM for a dynamic group.
* A group policy file needed to create a member in a dynamic group.
* A group policy file needed to create a member in a static group (i.e. a group where the member list is predefined and there is no MGM).

## MGM Group Policy

​Since we need a CPI to create a virtual node, we need to have a group policy file for an MGM. Most of the information in a group policy file is exported by the MGM, so in the case of the initial MGM group policy it will be a much smaller file than that needed to create a member.

​All of the information defining the group will be passed to the MGM during onboarding, and from that information the MGM can build and export a group policy file for members to use.

The group policy file used by the MGM just needs a flag to indicate that a group ID must be generated during virtual node onboarding and information about how to register itself as part of the group. Registration for an MGM is more like finalising the group set up, but the registration terminology is kept in line with the member set-up.

This is a simple file and could be manually constructed, and the `corda-cli` tooling could export a default version of this.

## Dynamic Network Member Group Policy file

​ Members wishing to join a group must use a group policy file that was exported from the MGM of that group. This group policy file will contain much more information than the initial file used by the MGM. It will contain the group ID, the protocol to join that group, the protocol to use to sync data with the MGM, and any additional parameters that are needed for these protocols.

​ The full MemberInfo of the MGM will be included in this file so that it can be populated in the member's member list cache in order to allow communication with the MGM during registration. When the member's registration has been approved, the full MGM `MemberInfo` will be distributed among the group data distributed. The MGM info in the group policy is intended as bootstrap data for joining the group. If the MGM info changes and the data in the CPI are no longer enough to join a group, a new CPI with an updated group policy must be created. For example, if the MGM endpoint or trust store changes. The data is in the form of the `LayeredPropertyMap`. i.e. `Map<String, String>`. This format is how the data is stored within Corda and is straightforward to understand when reading the file. Storing this way avoids the need for custom data converters just for building/parsing the group policy.

​ The member also requires information to configure the P2P layer to allow communication with other members and the MGM. This includes information such as trust roots and the PKI mode to use.

​ Since the MGM must create this file, an MGM must be up and running before this file can be created.

### Static Network Member Group Policy file

​ In the case of a static network, no MGM is managing the group, and the member list is statically set before the members are created. Hence, the group policy file for a static network member is similar to the dynamic network member's group policy with some differences.

​ The file will include the group ID, registration protocol, sync protocol, protocol parameters and P2P parameters, just like the dynamic member's group policy file. (Note: the static networks are static in how the member list is managed, and members can still communicate over P2P, which is why we need P2P parameters still, although it may be a reduced set since communication is only within a single cluster).

​ The differences with the MGM group policy file are regarding the necessary p2p parameters, protocol parameters and the MGM information. Since there is no MGM there is no MGM information included. The registration protocol used for static networks doesn't communicate with an MGM, but instead, to keep similar behaviour to a dynamic network, the static registration protocol takes a predefined member list from the protocol parameters and populates the member list using this.

​ For static networks, we will only support single cluster groups, so it may be possible to run using a subset of P2P parameters. For example, communication will be done without e2e/TLS session handshakes, so we do not need to configure this for static.

# Versioning

​ The group policy file may change over time as requirements change. To ensure we handle this, the group policy file format will be restricted by a JSON schema. This schema will be part of the [`corda-api` repo](https://github.com/corda/corda-api/tree/release/os/5.0/data/membership-schema/src/main/resources/net/corda/schema/membership/group/policy), and the package it is in will be versioned. Each time the schema evolves, a new version is created. The version number will be a whole number incremented on each change (i.e. 1, 2, 3, 4, etc.)

​ When parsing a group policy file, the file format version number must be at the root level so that the version can be detected and the structure can be validated against the correct schema. This should remain at the root level through all evolutions of the schema.

​ To assist with validation, service will need to be available to validate the policy files for being well formed. This may be joined with the group policy provider as different implementations of the `GroupPolicy` interface will need to be created to support different versions of the group policy file. This validation lib component will be used during CPI installation. If the group policy is invalid, the CPI installation fails before CPI data is persisted.

​
# Samples

Below are samples of group policy files for each of the three scenarios described above.

## MGM

``` json
{
  "fileFormatVersion": 1,
  "groupId": "CREATE_ID",
  "registrationProtocol": "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}
```

Or with optional parameters:
``` json
{
  "fileFormatVersion": 1,
  "groupId": "CREATE_ID",
  "registrationProtocol": "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl",
  "protocolParameters": {
    "foo": "bar"
  }
}
```

## Dynamic Group Member

**_NOTE:_**: One can export the group policy file from the MGM using the API instead of building it manually. See details in [the dynamic member onboarding page](../Member-Onboarding-(Dynamic-Networks))

**_NOTE:_**: Certificates and keys are edited for readability. A full sample can be seen in Appendix A

``` json
{
  "fileFormatVersion" : 1,
  "groupId" : "b48a7c1d-b7fd-4f75-9dd8-8cd604dd9221",
  "registrationProtocol" : "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "synchronisationProtocol" : "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "protocolParameters" : {
    "sessionKeyPolicy" : "Distinct"
  },
  "p2pParameters" : {
    "tlsTrustRoots" : [ "-----BEGIN CERTIFICATE-----\nMIIDuz...G9XCMIFdoiQ=\n-----END CERTIFICATE-----\n" ],
    "sessionPki" : "NoPKI",
    "tlsPki" : "Standard",
    "tlsVersion" : "1.3",
    "protocolMode" : "Authenticated_Encryption"
  },
  "mgmInfo" : {
    "corda.ecdh.key" : "-----BEGIN PUBLIC KEY-----\nMFkwEwYHK...WRKYsm8i+HHg==\n-----END PUBLIC KEY-----\n",
    "corda.endpoints.0.connectionURL" : "https://corda-p2p-gateway-worker.mgm-cluster:8080",
    "corda.endpoints.0.protocolVersion" : "1",
    "corda.groupId" : "b48a7c1d-b7fd-4f75-9dd8-8cd604dd9221",
    "corda.name" : "OU=8f4c40e8-329e-4ac9-b1fe-b151e082fd8b, O=Mgm, L=London, C=GB",
    "corda.platformVersion" : "5000",
    "corda.serial" : "1",
    "corda.session.key" : "-----BEGIN PUBLIC KEY-----\nMFkwEwoZ...KYsm8i+HHg==\n-----END PUBLIC KEY-----\n",
    "corda.session.key.hash" : "287445D6DD1FC3DBB6FB0A43E868379653A6D63694190F09C54F9B605042F485",
    "corda.softwareVersion" : "5.0.0"
  },
  "cipherSuite" : { }
}
```

## Static Group Member

**_NOTE:_**: One can build the group policy file using the `corda-cli` plugin. See details in [the plugin readme file](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/tools/plugins/mgm/README.md)

**_NOTE:_**: Certificates and keys are edited for readability. A full sample can be seen in Appendix B

``` json
{
  "fileFormatVersion" : 1,
  "groupId" : "b083fdae-4137-40ad-87e3-51b4f7176e7a",
  "registrationProtocol" : "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
  "synchronisationProtocol" : "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
  "protocolParameters" : {
    "sessionKeyPolicy" : "Combined",
    "staticNetwork" : {
      "members" : [
        {
          "name" : "C=GB, L=London, O=Alice",
          "memberStatus" : "ACTIVE",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        },
        {
          "name" : "C=GB, L=London, O=Bob",
          "memberStatus" : "ACTIVE",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        },
        {
          "name" : "C=GB, L=London, O=Charlie",
          "memberStatus" : "SUSPENDED",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        }
      ]
    }
  },
  "p2pParameters" : {
    "sessionTrustRoots" : [
      "-----BEGIN CERTIFICATE-----\nMIIFKTCC...d5mgaA=\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFFjCC...7hHwg==\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFYDCC...y753ec5\n-----END CERTIFICATE-----\n"
    ],
    "tlsTrustRoots" : [
      "-----BEGIN CERTIFICATE-----\nMIIFHjCCBAa...q0Qu\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFFjCCAv6...wg==\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFYDCCBEi...3ec5\n-----END CERTIFICATE-----\n"
    ],
    "sessionPki" : "Standard",
    "tlsPki" : "Standard",
    "tlsVersion" : "1.3",
    "protocolMode" : "Authenticated_Encryption"
  },
  "cipherSuite" : {
    "corda.provider" : "default",
    "corda.signature.provider" : "default",
    "corda.signature.default" : "ECDSA_SECP256K1_SHA256",
    "corda.signature.FRESH_KEYS" : "ECDSA_SECP256K1_SHA256",
    "corda.digest.default" : "SHA256",
    "corda.cryptoservice.provider" : "default"
  }
}
```

### Appendix A: Sample Group Policy for Dynamic Group without truncated data

``` json
{
  "fileFormatVersion" : 1,
  "groupId" : "b48a7c1d-b7fd-4f75-9dd8-8cd604dd9221",
  "registrationProtocol" : "net.corda.membership.impl.registration.dynamic.member.DynamicMemberRegistrationService",
  "synchronisationProtocol" : "net.corda.membership.impl.synchronisation.MemberSynchronisationServiceImpl",
  "protocolParameters" : {
    "sessionKeyPolicy" : "Distinct"
  },
  "p2pParameters" : {
    "tlsTrustRoots" : [ "-----BEGIN CERTIFICATE-----\nMIIDuzCCAiOgAwIBAgIBAjANBgkqhkiG9w0BAQsFADAQMQ4wDAYDVQQGEwVVSyBD\nTjAeFw0yMjA5MjkwODE3MTlaFw0yMjEwMjkwODE3MTlaMBAxDjAMBgNVBAYTBVVL\nIENOMIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAnUc4VXBpwjv4f6/e\n861AB4+MaBum46M+hkj3V14+RX/zT2vG7ddQtA+p+8urctM+Fg3rvrjCxSGWXR7A\n/K+JuGo1QFG4/t26Tgv2eliwPDZC0dgqofVw4aWCzjFy1PxPEdkdcrteTetE63kT\n/bCPsrgQFSp8bZc3UQhJGXQ+QdEblS3OUEZvn2WPtlWH3eZeb5kI1pmJF3bsdoit\npmsnNkkJ5DEnUEdnI3qXYldyyAEEdEO8rfqpUCtrXSWrKIqW1s8CmgwGeSFPBTyt\nKkwTk+PhFd8QAPnJEQM1PBAZ0dyR3yvb/77HDWf7NnY22W+iu76Jya0nXxe2hJPd\ny1QrxLV2vRAVpm6ZrKhzhqpRV8Jev8ftA2vseCijDieB4LQY8bHKrEgw13NMgAli\n1J5LKvN9q1mWJnwBm7n/1CHYSLpzdBHZPSmzVdS3E/lz/xw7i5rzBiE4/th8KR8r\nmN838dtUYXOPndB72QFyeNtseOdTc/bK615wRwVNQ8QfcBjHAgMBAAGjIDAeMA8G\nA1UdEwEB/wQFMAMBAf8wCwYDVR0PBAQDAgGuMA0GCSqGSIb3DQEBCwUAA4IBgQB/\n0FPRuk1lvV6IoSr4mjBjG0KEB/WhH4aUALJbwYbR0mjxRklX4pKO61SNlRLg5wGn\neO/aWJgkTpz63tu2rhpPGXvIpi7Ik41/qtSlhm6m9izNqAlZ5rWzXj4bpXTjRl/7\nPLpHHBJDRB/GSZTQ65/Kh9XuDsRUrsVegXSwWgcQ4Uh76i7Yuz4VyWu/i4TQjP45\nxhCL2vnIbRoI72LjFE3MoULtJ5UsqJRKplcdo6ukMExDar/Da23xz8W6WfcQrPml\n+HEheubJmL4gC91I8dG6zhIk5XPL6PFK2CzJ6fybI0eHy3lGzUT+WC7VpScG07jt\n597Af3EWKNTFS8rQDabVhEmzDJ0tje/dWbMct53dMgG/h0QDR/JL/bN3fyvFnLyh\ncBzvjL2t1oM51Nl5Y3C0Nufq2OzLjtA02Dw7VtsYZIPQ9yRckJ+OwkxU7bvowsOB\ncuNlSf8i4wLgJqf3hvmUyPl/0i0dVRb5azeL/vv0wul1wfnypRgmG9XCMIFdoiQ=\n-----END CERTIFICATE-----\n" ],
    "sessionPki" : "NoPKI",
    "tlsPki" : "Standard",
    "tlsVersion" : "1.3",
    "protocolMode" : "Authenticated_Encryption"
  },
  "mgmInfo" : {
    "corda.ecdh.key" : "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEakDk0o9I12P7amqv/1WTBVAcgoZ4\nlOFpY3YFXJ68HqNRViXpgWE2mfxtSFSwqeSLoAHei+2WZcWRKYsm8i+HHg==\n-----END PUBLIC KEY-----\n",
    "corda.endpoints.0.connectionURL" : "https://corda-p2p-gateway-worker.mgm-cluster:8080",
    "corda.endpoints.0.protocolVersion" : "1",
    "corda.groupId" : "b48a7c1d-b7fd-4f75-9dd8-8cd604dd9221",
    "corda.name" : "OU=8f4c40e8-329e-4ac9-b1fe-b151e082fd8b, O=Mgm, L=London, C=GB",
    "corda.platformVersion" : "5000",
    "corda.serial" : "1",
    "corda.session.key" : "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEakDk0o9I12P7amqv/1WTBVAcgoZ4\nlOFpY3YFXJ68HqNRViXpgWE2mfxtSFSwqeSLoAHei+2WZcWRKYsm8i+HHg==\n-----END PUBLIC KEY-----\n",
    "corda.session.key.hash" : "287445D6DD1FC3DBB6FB0A43E868379653A6D63694190F09C54F9B605042F485",
    "corda.softwareVersion" : "5.0.0"
  },
  "cipherSuite" : { }
}
```

### Appendix B: Sample Group Policy for Static Group without truncated data

``` json
{
  "fileFormatVersion" : 1,
  "groupId" : "b083fdae-4137-40ad-87e3-51b4f7176e7a",
  "registrationProtocol" : "net.corda.membership.impl.registration.staticnetwork.StaticMemberRegistrationService",
  "synchronisationProtocol" : "net.corda.membership.impl.sync.staticnetwork.StaticMemberSyncService",
  "protocolParameters" : {
    "sessionKeyPolicy" : "Combined",
    "staticNetwork" : {
      "members" : [
        {
          "name" : "C=GB, L=London, O=Alice",
          "memberStatus" : "ACTIVE",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        },
        {
          "name" : "C=GB, L=London, O=Bob",
          "memberStatus" : "ACTIVE",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        },
        {
          "name" : "C=GB, L=London, O=Charlie",
          "memberStatus" : "ACTIVE",
          "endpointUrl-1" : "https://corda5.r3.com:10000",
          "endpointProtocol-1" : 5
        }
      ]
    }
  },
  "p2pParameters" : {
    "sessionTrustRoots" : [
      "-----BEGIN CERTIFICATE-----\nMIIFKTCCBBGgAwIBAgISBPBWAQX74sKyWaxrwN9Wyf/4MA0GCSqGSIb3DQEBCwUA\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\nEwJSMzAeFw0yMjA1MTMxMTE0NTlaFw0yMjA4MTExMTE0NThaMBQxEjAQBgNVBAMT\nCWNvcmRhLm5ldDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMO\nna/+r0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmi\nVzKn6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoC\nDnKgzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEH\nsxMK+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lF\nZ2roWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gz\nMHdfNwDftNqTuMkCAwEAAaOCAlUwggJRMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUE\nFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQU\ntd2w7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+v\nnYsUwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5s\nZW5jci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wIwYD\nVR0RBBwwGoIJY29yZGEubmV0gg13d3cuY29yZGEubmV0MEwGA1UdIARFMEMwCAYG\nZ4EMAQIBMDcGCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMu\nbGV0c2VuY3J5cHQub3JnMIIBBgYKKwYBBAHWeQIEAgSB9wSB9ADyAHcA36Veq2iC\nTx9sre64X04+WurNohKkal6OOxLAIERcKnMAAAGAvVf0zgAABAMASDBGAiEA7LTc\nKcc22HaRFQBqt5zCQjdUcuuZCzbDuhYfL7zbeW4CIQC/Jw3uq7nj1XjpPVb8amYO\nZBaIyLtqvfdLpnSvIe+NowB3ACl5vvCeOTkh8FZzn2Old+W+V32cYAr4+U1dJlwl\nXceEAAABgL1X9L0AAAQDAEgwRgIhALp82uqQgsTTSGoQ44obZdgin8eLrUb0fnJX\nuiOEjeIMAiEA4GM7LhToVLb7+EtEoCtkH7Mwr8rsmTV9oXYzjXuWUfQwDQYJKoZI\nhvcNAQELBQADggEBAHMyXmq77uYcC/cvT1QFzZvjrohxeZQHzYWsIho6DfpS8RZd\nN+O1sa4/tjMNN5XSrAY7YJczgBue13YH+Vw9k8hVqJ7vHKSbFbMrF03NgHLfM2rv\nCHPCZCv3zqESdkcNaXNYDykcwpZjmUFV8T2gy8se+3FYfgiDr6lfpUIDF47EaD9S\nIFv3D2+FNNS2VaC2U2Uta1XQkrdkUznq8A4rTY3RTTjlMhXf2OP19eUqsmFKF+5D\nfMTdCNm5Klag/h/ogvYRXxYFvr+4l5hOzK1IJJWoftGi4s1f1pgv/sbi2DXKNPOP\n7oKylBF5li7LtauuKA6rZM3S62LJvt/Y+d5mgaA=\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\nWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\nRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\nAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\nR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\nsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\nNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\nZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\n/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\nAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\nAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\nFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\nAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\nOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\ngt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\nPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\nikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\nCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\nlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\navAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\nyJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\nyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\nhCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\nHlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\nMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\nnLRbwHOoq7hHwg==\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFYDCCBEigAwIBAgIQQAF3ITfU6UK47naqPGQKtzANBgkqhkiG9w0BAQsFADA/\nMSQwIgYDVQQKExtEaWdpdGFsIFNpZ25hdHVyZSBUcnVzdCBDby4xFzAVBgNVBAMT\nDkRTVCBSb290IENBIFgzMB4XDTIxMDEyMDE5MTQwM1oXDTI0MDkzMDE4MTQwM1ow\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwggIiMA0GCSqGSIb3DQEB\nAQUAA4ICDwAwggIKAoICAQCt6CRz9BQ385ueK1coHIe+3LffOJCMbjzmV6B493XC\nov71am72AE8o295ohmxEk7axY/0UEmu/H9LqMZshftEzPLpI9d1537O4/xLxIZpL\nwYqGcWlKZmZsj348cL+tKSIG8+TA5oCu4kuPt5l+lAOf00eXfJlII1PoOK5PCm+D\nLtFJV4yAdLbaL9A4jXsDcCEbdfIwPPqPrt3aY6vrFk/CjhFLfs8L6P+1dy70sntK\n4EwSJQxwjQMpoOFTJOwT2e4ZvxCzSow/iaNhUd6shweU9GNx7C7ib1uYgeGJXDR5\nbHbvO5BieebbpJovJsXQEOEO3tkQjhb7t/eo98flAgeYjzYIlefiN5YNNnWe+w5y\nsR2bvAP5SQXYgd0FtCrWQemsAXaVCg/Y39W9Eh81LygXbNKYwagJZHduRze6zqxZ\nXmidf3LWicUGQSk+WT7dJvUkyRGnWqNMQB9GoZm1pzpRboY7nn1ypxIFeFntPlF4\nFQsDj43QLwWyPntKHEtzBRL8xurgUBN8Q5N0s8p0544fAQjQMNRbcTa0B7rBMDBc\nSLeCO5imfWCKoqMpgsy6vYMEG6KDA0Gh1gXxG8K28Kh8hjtGqEgqiNx2mna/H2ql\nPRmP6zjzZN7IKw0KKP/32+IVQtQi0Cdd4Xn+GOdwiK1O5tmLOsbdJ1Fu/7xk9TND\nTwIDAQABo4IBRjCCAUIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYw\nSwYIKwYBBQUHAQEEPzA9MDsGCCsGAQUFBzAChi9odHRwOi8vYXBwcy5pZGVudHJ1\nc3QuY29tL3Jvb3RzL2RzdHJvb3RjYXgzLnA3YzAfBgNVHSMEGDAWgBTEp7Gkeyxx\n+tvhS5B1/8QVYIWJEDBUBgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEB\nATAwMC4GCCsGAQUFBwIBFiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQu\nb3JnMDwGA1UdHwQ1MDMwMaAvoC2GK2h0dHA6Ly9jcmwuaWRlbnRydXN0LmNvbS9E\nU1RST09UQ0FYM0NSTC5jcmwwHQYDVR0OBBYEFHm0WeZ7tuXkAXOACIjIGlj26Ztu\nMA0GCSqGSIb3DQEBCwUAA4IBAQAKcwBslm7/DlLQrt2M51oGrS+o44+/yQoDFVDC\n5WxCu2+b9LRPwkSICHXM6webFGJueN7sJ7o5XPWioW5WlHAQU7G75K/QosMrAdSW\n9MUgNTP52GE24HGNtLi1qoJFlcDyqSMo59ahy2cI2qBDLKobkx/J3vWraV0T9VuG\nWCLKTVXkcGdtwlfFRjlBz4pYg1htmf5X6DYO8A4jqv2Il9DjXA6USbW1FzXSLr9O\nhe8Y4IWS6wY7bCkjCWDcRQJMEhg76fsO3txE+FiYruq9RUWhiF1myv4Q6W+CyBFC\nDfvp7OOGAN6dEOM4+qR9sdjoSYKEBpsr6GtPAQw4dy753ec5\n-----END CERTIFICATE-----\n"
    ],
    "tlsTrustRoots" : [
      "-----BEGIN CERTIFICATE-----\nMIIFHjCCBAagAwIBAgISAy1ybKW9u73QQxLAktLHTEQQMA0GCSqGSIb3DQEBCwUA\nMDIxCzAJBgNVBAYTAlVTMRYwFAYDVQQKEw1MZXQncyBFbmNyeXB0MQswCQYDVQQD\nEwJSMzAeFw0yMjA1MTExNTAxMDZaFw0yMjA4MDkxNTAxMDVaMBExDzANBgNVBAMT\nBnIzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMqmvfMOna/+\nr0V3d3hpGPz5hesAAJRZjJCjsQr5ly8LodIfcPRSz+p5N8ui6ct8lyOmGLmiVzKn\n6h+On4ilNnd2inIqBRcyFlU4YFyBqq9+FZdR64gEr2CVX8xDz5bMFymLZJoCDnKg\nzq6LAvhQv/2NIkSRuLI09phKhMwQkAzFaOx0Q1kkmNnJYSf81dF1lbTVAAEHsxMK\n+4dGECQCYFsfkrpk4wVBnaIdr7JLsrOHbbdLK8Ks/TxVNw20FOvuKZzR28lFZ2ro\nWY7S3s+x6mNZk4zhmTkBFXR747q7IVqj+Un3BU2G5/2TZ6LCJ+8m3WPD+9gzMHdf\nNwDftNqTuMkCAwEAAaOCAk0wggJJMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAU\nBggrBgEFBQcDAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUtd2w\n7gkV6EYKUVTrXLPbKNpIc1UwHwYDVR0jBBgwFoAUFC6zF7dYVsuuUAlA5h+vnYsU\nwsYwVQYIKwYBBQUHAQEESTBHMCEGCCsGAQUFBzABhhVodHRwOi8vcjMuby5sZW5j\nci5vcmcwIgYIKwYBBQUHMAKGFmh0dHA6Ly9yMy5pLmxlbmNyLm9yZy8wHQYDVR0R\nBBYwFIIGcjMuY29tggp3d3cucjMuY29tMEwGA1UdIARFMEMwCAYGZ4EMAQIBMDcG\nCysGAQQBgt8TAQEBMCgwJgYIKwYBBQUHAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5\ncHQub3JnMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHYAKXm+8J45OSHwVnOfY6V3\n5b5XfZxgCvj5TV0mXCVdx4QAAAGAs9o+JQAABAMARzBFAiEAi07Xbw6nqHBtGQzN\nLXbCPx68E2xYa9M/ytztzJb96IYCIAiIc9y7u2H510F8AQ1zon7wDQjaTTvL3Ezl\nJBgFK02aAHYAQcjKsd8iRkoQxqE6CUKHXk4xixsD6+tLx2jwkGKWBvYAAAGAs9o+\nZwAABAMARzBFAiEAyO7PeW40ocwt+QqSMZAJHKRe7Ip1kYkjUhabhVQD0CoCIBpD\nqJEJd3UlGIUyxJ44i72xQ6kvn5adfnmJE5Jh8YwPMA0GCSqGSIb3DQEBCwUAA4IB\nAQBRENd2mg7C73zwxAduIDcYaQ+bKaM9+edHBC+h7cDSACdQ1J+AKruWWYOfQJXG\nQvudeDU2W7+kUC/0fq0Ui9cGCQBY+EacFN6261z2jVLtdGwJWRe2pYwIVOdknFet\nMY31Fqih/HToiaX1Fz0qkN0TrdLBsMIZEx3XAiMHbJH4AOrr+V2FpV6GIAZ1A68I\nFkR4W6zPnI7cwjpeLnO6x1A92y5txtNBeBu0DDnbt695J8BVZeJBei0gIVe3y1Xf\n2xPJfCYMCpysD6ADGOmMrahZ4ANfDck27hIDw8GXYDBp8XP7teM7r/OVRJW5MJUK\nxcTyC7ANrJ7GGChxJZUaq0Qu\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFFjCCAv6gAwIBAgIRAJErCErPDBinU/bWLiWnX1owDQYJKoZIhvcNAQELBQAw\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwHhcNMjAwOTA0MDAwMDAw\nWhcNMjUwOTE1MTYwMDAwWjAyMQswCQYDVQQGEwJVUzEWMBQGA1UEChMNTGV0J3Mg\nRW5jcnlwdDELMAkGA1UEAxMCUjMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\nAoIBAQC7AhUozPaglNMPEuyNVZLD+ILxmaZ6QoinXSaqtSu5xUyxr45r+XXIo9cP\nR5QUVTVXjJ6oojkZ9YI8QqlObvU7wy7bjcCwXPNZOOftz2nwWgsbvsCUJCWH+jdx\nsxPnHKzhm+/b5DtFUkWWqcFTzjTIUu61ru2P3mBw4qVUq7ZtDpelQDRrK9O8Zutm\nNHz6a4uPVymZ+DAXXbpyb/uBxa3Shlg9F8fnCbvxK/eG3MHacV3URuPMrSXBiLxg\nZ3Vms/EY96Jc5lP/Ooi2R6X/ExjqmAl3P51T+c8B5fWmcBcUr2Ok/5mzk53cU6cG\n/kiFHaFpriV1uxPMUgP17VGhi9sVAgMBAAGjggEIMIIBBDAOBgNVHQ8BAf8EBAMC\nAYYwHQYDVR0lBBYwFAYIKwYBBQUHAwIGCCsGAQUFBwMBMBIGA1UdEwEB/wQIMAYB\nAf8CAQAwHQYDVR0OBBYEFBQusxe3WFbLrlAJQOYfr52LFMLGMB8GA1UdIwQYMBaA\nFHm0WeZ7tuXkAXOACIjIGlj26ZtuMDIGCCsGAQUFBwEBBCYwJDAiBggrBgEFBQcw\nAoYWaHR0cDovL3gxLmkubGVuY3Iub3JnLzAnBgNVHR8EIDAeMBygGqAYhhZodHRw\nOi8veDEuYy5sZW5jci5vcmcvMCIGA1UdIAQbMBkwCAYGZ4EMAQIBMA0GCysGAQQB\ngt8TAQEBMA0GCSqGSIb3DQEBCwUAA4ICAQCFyk5HPqP3hUSFvNVneLKYY611TR6W\nPTNlclQtgaDqw+34IL9fzLdwALduO/ZelN7kIJ+m74uyA+eitRY8kc607TkC53wl\nikfmZW4/RvTZ8M6UK+5UzhK8jCdLuMGYL6KvzXGRSgi3yLgjewQtCPkIVz6D2QQz\nCkcheAmCJ8MqyJu5zlzyZMjAvnnAT45tRAxekrsu94sQ4egdRCnbWSDtY7kh+BIm\nlJNXoB1lBMEKIq4QDUOXoRgffuDghje1WrG9ML+Hbisq/yFOGwXD9RiX8F6sw6W4\navAuvDszue5L3sz85K+EC4Y/wFVDNvZo4TYXao6Z0f+lQKc0t8DQYzk1OXVu8rp2\nyJMC6alLbBfODALZvYH7n7do1AZls4I9d1P4jnkDrQoxB3UqQ9hVl3LEKQ73xF1O\nyK5GhDDX8oVfGKF5u+decIsH4YaTw7mP3GFxJSqv3+0lUFJoi5Lc5da149p90Ids\nhCExroL1+7mryIkXPeFM5TgO9r0rvZaBFOvV2z0gp35Z0+L4WPlbuEjN/lxPFin+\nHlUjr8gRsI3qfJOQFy/9rKIJR0Y/8Omwt/8oTWgy1mdeHmmjk7j1nYsvC9JSQ6Zv\nMldlTTKB3zhThV1+XWYp6rjd5JW1zbVWEkLNxE7GJThEUG3szgBVGP7pSWTUTsqX\nnLRbwHOoq7hHwg==\n-----END CERTIFICATE-----\n",
      "-----BEGIN CERTIFICATE-----\nMIIFYDCCBEigAwIBAgIQQAF3ITfU6UK47naqPGQKtzANBgkqhkiG9w0BAQsFADA/\nMSQwIgYDVQQKExtEaWdpdGFsIFNpZ25hdHVyZSBUcnVzdCBDby4xFzAVBgNVBAMT\nDkRTVCBSb290IENBIFgzMB4XDTIxMDEyMDE5MTQwM1oXDTI0MDkzMDE4MTQwM1ow\nTzELMAkGA1UEBhMCVVMxKTAnBgNVBAoTIEludGVybmV0IFNlY3VyaXR5IFJlc2Vh\ncmNoIEdyb3VwMRUwEwYDVQQDEwxJU1JHIFJvb3QgWDEwggIiMA0GCSqGSIb3DQEB\nAQUAA4ICDwAwggIKAoICAQCt6CRz9BQ385ueK1coHIe+3LffOJCMbjzmV6B493XC\nov71am72AE8o295ohmxEk7axY/0UEmu/H9LqMZshftEzPLpI9d1537O4/xLxIZpL\nwYqGcWlKZmZsj348cL+tKSIG8+TA5oCu4kuPt5l+lAOf00eXfJlII1PoOK5PCm+D\nLtFJV4yAdLbaL9A4jXsDcCEbdfIwPPqPrt3aY6vrFk/CjhFLfs8L6P+1dy70sntK\n4EwSJQxwjQMpoOFTJOwT2e4ZvxCzSow/iaNhUd6shweU9GNx7C7ib1uYgeGJXDR5\nbHbvO5BieebbpJovJsXQEOEO3tkQjhb7t/eo98flAgeYjzYIlefiN5YNNnWe+w5y\nsR2bvAP5SQXYgd0FtCrWQemsAXaVCg/Y39W9Eh81LygXbNKYwagJZHduRze6zqxZ\nXmidf3LWicUGQSk+WT7dJvUkyRGnWqNMQB9GoZm1pzpRboY7nn1ypxIFeFntPlF4\nFQsDj43QLwWyPntKHEtzBRL8xurgUBN8Q5N0s8p0544fAQjQMNRbcTa0B7rBMDBc\nSLeCO5imfWCKoqMpgsy6vYMEG6KDA0Gh1gXxG8K28Kh8hjtGqEgqiNx2mna/H2ql\nPRmP6zjzZN7IKw0KKP/32+IVQtQi0Cdd4Xn+GOdwiK1O5tmLOsbdJ1Fu/7xk9TND\nTwIDAQABo4IBRjCCAUIwDwYDVR0TAQH/BAUwAwEB/zAOBgNVHQ8BAf8EBAMCAQYw\nSwYIKwYBBQUHAQEEPzA9MDsGCCsGAQUFBzAChi9odHRwOi8vYXBwcy5pZGVudHJ1\nc3QuY29tL3Jvb3RzL2RzdHJvb3RjYXgzLnA3YzAfBgNVHSMEGDAWgBTEp7Gkeyxx\n+tvhS5B1/8QVYIWJEDBUBgNVHSAETTBLMAgGBmeBDAECATA/BgsrBgEEAYLfEwEB\nATAwMC4GCCsGAQUFBwIBFiJodHRwOi8vY3BzLnJvb3QteDEubGV0c2VuY3J5cHQu\nb3JnMDwGA1UdHwQ1MDMwMaAvoC2GK2h0dHA6Ly9jcmwuaWRlbnRydXN0LmNvbS9E\nU1RST09UQ0FYM0NSTC5jcmwwHQYDVR0OBBYEFHm0WeZ7tuXkAXOACIjIGlj26Ztu\nMA0GCSqGSIb3DQEBCwUAA4IBAQAKcwBslm7/DlLQrt2M51oGrS+o44+/yQoDFVDC\n5WxCu2+b9LRPwkSICHXM6webFGJueN7sJ7o5XPWioW5WlHAQU7G75K/QosMrAdSW\n9MUgNTP52GE24HGNtLi1qoJFlcDyqSMo59ahy2cI2qBDLKobkx/J3vWraV0T9VuG\nWCLKTVXkcGdtwlfFRjlBz4pYg1htmf5X6DYO8A4jqv2Il9DjXA6USbW1FzXSLr9O\nhe8Y4IWS6wY7bCkjCWDcRQJMEhg76fsO3txE+FiYruq9RUWhiF1myv4Q6W+CyBFC\nDfvp7OOGAN6dEOM4+qR9sdjoSYKEBpsr6GtPAQw4dy753ec5\n-----END CERTIFICATE-----\n"
    ],
    "sessionPki" : "Standard",
    "tlsPki" : "Standard",
    "tlsVersion" : "1.3",
    "protocolMode" : "Authenticated_Encryption"
  },
  "cipherSuite" : {
    "corda.provider" : "default",
    "corda.signature.provider" : "default",
    "corda.signature.default" : "ECDSA_SECP256K1_SHA256",
    "corda.signature.FRESH_KEYS" : "ECDSA_SECP256K1_SHA256",
    "corda.digest.default" : "SHA256",
    "corda.cryptoservice.provider" : "default"
  }
}

```