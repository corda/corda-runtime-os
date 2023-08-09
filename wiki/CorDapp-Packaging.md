# CorDapp Packaging

## Introduction

This document describes how to build format version 2 CPKs, CPBs and CPIs.

## Before you start

You will need a working copy of `corda-cli` with the mgm and package plugins installed. See https://github.com/corda/corda-cli-plugin-host#setupbuild.

## Configure the plugin in gradle

This describes how to convert an existing CorDapp project to the new Gradle plugin.
 
1. Add a new version number to `gradle.properties`:
    ```groovy
    cordaGradlePluginsVersion2=7.0.3
    ```
1. Add this repository to pluginManagement/repositories in `settings.gradle`:
    ```groovy
    maven {
        url "${artifactoryContextUrl}/corda-dev"
        content {
            includeGroupByRegex 'net\\.corda\\.plugins(\\..*)?'
        }
    }
    ```
1. Add the plugin to the plugins section of `settings.gradle`:
    ```groovy
    id 'net.corda.plugins.cordapp-cpk2' version cordaGradlePluginsVersion2
    id 'net.corda.plugins.cordapp-cpb2' version cordaGradlePluginsVersion2
    ```
1. Inside the cordapp project change the plugins block at the top of the file:
    ```groovy
    id 'net.corda.plugins.cordapp-cpk2'
    // or
    id 'net.corda.plugins.cordapp-cpb2'
    ```

## Build a CPK

To build a CPK, configure the project with either `cordapp-cpk2` or `cordapp-cpb2` plugin and run the `jar` Gradle task.

```shell
./gradlew jar
```

## Build a CPB

To build a CPB, configure the project with the `cordapp-cpb2` plugin and run the `cpb` Gradle task.

```shell
./gradlew cpb
```

## CPI Build Preparation (only needed first time)

### Signing key setup

We need to generate a code signing key for signing the CPI. This key can be generated once and kept for reuse.
 
Generate a signing key:
```shell
keytool -genkeypair -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, L=London, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000
```

### Trust the signing keys

The following signing keys are required only in the specific circumstances depending on your signing setup and the type of your CPB.

#### Trust the Gradle plugin default signing key

The Gradle plugin can be configured with a cordapp/signing section to define which signing keys the CPK and CPB are signed with. If that section is missing from configuration a default signing key will be used.

If we are using the default signing key, we need to import it into our key store before building a CPI.

1. Save the following text into a file named `gradle-plugin-default-key.pem`
    ```text
    -----BEGIN CERTIFICATE-----
    MIIB7zCCAZOgAwIBAgIEFyV7dzAMBggqhkjOPQQDAgUAMFsxCzAJBgNVBAYTAkdC
    MQ8wDQYDVQQHDAZMb25kb24xDjAMBgNVBAoMBUNvcmRhMQswCQYDVQQLDAJSMzEe
    MBwGA1UEAwwVQ29yZGEgRGV2IENvZGUgU2lnbmVyMB4XDTIwMDYyNTE4NTI1NFoX
    DTMwMDYyMzE4NTI1NFowWzELMAkGA1UEBhMCR0IxDzANBgNVBAcTBkxvbmRvbjEO
    MAwGA1UEChMFQ29yZGExCzAJBgNVBAsTAlIzMR4wHAYDVQQDExVDb3JkYSBEZXYg
    Q29kZSBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQDjSJtzQ+ldDFt
    pHiqdSJebOGPZcvZbmC/PIJRsZZUF1bl3PfMqyG3EmAe0CeFAfLzPQtf2qTAnmJj
    lGTkkQhxo0MwQTATBgNVHSUEDDAKBggrBgEFBQcDAzALBgNVHQ8EBAMCB4AwHQYD
    VR0OBBYEFLMkL2nlYRLvgZZq7GIIqbe4df4pMAwGCCqGSM49BAMCBQADSAAwRQIh
    ALB0ipx6EplT1fbUKqgc7rjH+pV1RQ4oKF+TkfjPdxnAAiArBdAI15uI70wf+xlL
    zU+Rc5yMtcOY4/moZUq36r0Ilg==
    -----END CERTIFICATE-----
    ```
1. Import `gradle-plugin-default-key.pem` into the keystore
    ```shell
    keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias gradle-plugin-default-key -file gradle-plugin-default-key.pem
    ```

#### Trust the R3 signing key

If you are using _notary_ CPB provided by R3, it is signed with an R3 production code signing certificate issued by the DigiCert.
To use it import DigiCert certificate.

1. Save the following text into a file named `digicert-ca.pem`
    ```text
   -----BEGIN CERTIFICATE-----
   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
   -----END CERTIFICATE-----
    ```
1. Import `digicert-ca.pem` into the keystore
    ```shell
    keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias digicert-ca -file digicert-ca.pem
    ```

### Prepare a group policy file

Please refer to the group policy wiki page to learn more about the `GroupPolicy.json` file included in CPIs: [Group Policy](../wiki/Group-Policy)

If you intend to run a basic static network setup, you can use the corda-cli mgm plugin to generate a group policy file. For example, you could run this command to generate a group policy file:
```shell
./corda-cli.sh mgm groupPolicy > TestGroupPolicy.json
```
Refer to the plugin `README.md` for more information on this plugin: [README.md](https://github.com/corda/corda-runtime-os/blob/release/os/5.0/tools/plugins/mgm/README.md)

If running a dynamic network, you will need to export the group policy file from the MGM using the HTTP API. Information on how to do this can be found on the [MGM Onboarding](../wiki/MGM-Onboarding#export-group-policy-for-group) wiki page.

## Build a version 2 CPI

The gradle plugin will build the CPB. Run this command to turn a CPB into a CPI:
```shell
./corda-cli.sh package create-cpi \
    --cpb mycpb.cpb \
    --group-policy TestGroupPolicy.json \
    --cpi-name "cpi name" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
```

## Import trusted code signing certificates into Corda

Corda will validate that uploaded CPIs are signed with a trusted key. To trust your signing keys, upload them with these commands.

1. Import the DigiCert certificate into Corda if you are using notary CPB
   ```shell
   curl --insecure -u admin:admin -X PUT -F alias="digicert-ca" -F certificate=@digicert-ca.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
   ```
1. Export the signing key certificate from the key store
    ```shell
    keytool -exportcert -rfc -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -file signingkey1.pem
    ```
1. Import the signing key into Corda
    ```shell
    curl --insecure -u admin:admin -X PUT -F alias="signingkey1-2022" -F certificate=@signingkey1.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
    ```
    _Use an alias that will be unique over time. Consider how certificate expiry will require new certificates with the same x500 name as existing certificates and define a naming convention that covers that use case._
1. Import the gradle plugin default key into Corda
    ```shell
    curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
    ```
   **Note:** Do not import the gradle plugin key into the Corda in production. This should only be used in development.
