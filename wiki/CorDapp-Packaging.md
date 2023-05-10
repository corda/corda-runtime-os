# CorDapp Packaging

## Introduction

This document describes how to build format version 2 CPKs, CPBs and CPIs.

## Before you start

You will need a working copy of `corda-cli` with the mgm and package plugins installed. See https://github.com/corda/corda-cli-plugin-host#setupbuild.

## Configure the plugin in gradle

This describes how to convert an existing CorDapp project to the new Gradle plugin.
 
1. Add a new version number to `gradle.properties`:
    ```groovy
    cordaGradlePluginsVersion2=7.0.2
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

### Trust the Gradle plugin default signing key

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

### Trust the beta signing key

The notary CPB was signed with a test signing key for the beta build. To use it import this certificate.

1. Save the following text into a file named `beta-ca-root.pem`
    ```text
    -----BEGIN CERTIFICATE-----
    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
    0OXjjmtQ
    -----END CERTIFICATE-----
    ```
1. Import `beta-ca-root.pem` into the keystore
    ```shell
    keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias beta-ca-root -file beta-ca-root.pem
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

1. Import the gradle plugin default key into Corda
    ```shell
    curl --insecure -u admin:admin -X PUT -F alias="gradle-plugin-default-key" -F certificate=@gradle-plugin-default-key.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
    ```
1. Import the beta signing key into Corda
   ```shell
   curl --insecure -u admin:admin -X PUT -F alias="beta-ca-root" -F certificate=@beta-ca-root.pem https://localhost:8888/api/v1/certificates/cluster/code-signer
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
