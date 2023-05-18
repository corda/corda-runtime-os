# Package Plugin

This is a plug-in for [Corda CLI plug-in host](https://github.com/corda/corda-cli-plugin-host) for working with CPB and CPI files.

## Examples

These commands assume you have access to ./corda-cli.sh

### Generate two self-signed signing keys 

```shell
# Generate two self-signed signing keys to show how we select one key among multiple
keytool -genkeypair -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "CN=CPI Plugin Example - Signing Key 1, O=R3, L=London, C=GB" -keyalg RSA -storetype pkcs12 -validity 4000
keytool -genkeypair -alias "signing key 2" -keystore signingkeys.pfx -storepass "keystore password" -dname "CN=CPI Plugin Example - Signing Key 2, O=R3, L=London, C=GB" -keyalg RSA -storetype pkcs12 -validity 4000
```

### Trust the gradle plugin default signing key
Save the following content in a text file named `gradle-plugin-default-key.pem`
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
Then run the following command to import the certificate into the keystore:
```shell
keytool -importcert -keystore signingkeys.pfx -storepass "keystore password" -noprompt -alias gradle-plugin-default-key -file gradle-plugin-default-key.pem
```

### Trust your own signing key

The plugin does not currently trust the signing keys within the keystore when doing signature verification. To trust those keys, export them as certificates and import them into the keystore.

```shell
keytool -exportcert --keystore signingkeys.pfx --storepass "keystore password" -alias "signing key 1" -rfc -file signingkey1.crt
keytool -exportcert --keystore signingkeys.pfx --storepass "keystore password" -alias "signing key 2" -rfc -file signingkey2.crt

keytool --importcert --keystore signingkeys.pfx --storepass "keystore password" -alias "signing key 1 cert" --file signingkey1.crt
keytool --importcert --keystore signingkeys.pfx --storepass "keystore password" -alias "signing key 2 cert" --file signingkey2.crt
```

### Build a CPB
```shell
./corda-cli.sh package create-cpb \
    mycpk0.cpk mycpk1.cpk \
    --cpb-name manifest-attribute-cpb-name \
    --cpb-version manifest-attribute-cpb-version \
    --file output.cpb \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
```

### Build a group policy file
```shell
./corda-cli.sh mgm groupPolicy > TestGroupPolicy.json
```

### Verify a CPI
```shell
./corda-cli.sh package verify mycpi.cpi
```

### Sign a CPI or CPB or CPK

After QA, CorDapp developers will want to release sign their files. This command will remove existing (development) signatures and apply new signatures.

```shell
./corda-cli.sh package sign \
    mycpb.cpb \
    --file signed.cpb \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
```

### Build a CPI v2
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

### Pipe group policy into CPI v2
```shell
./corda-cli.sh mgm groupPolicy | ./corda-cli.sh package create-cpi \
    --cpb mycpb.cpb \
    --group-policy - \
    --cpi-name "cpi name" \
    --cpi-version "1.0.0.0-SNAPSHOT" \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1"
```

### Check signatures using jarsigner
```shell
jarsigner -keystore signingkeys.pfx -storepass "keystore password" -verbose -certs  -verify output.cpi
```

## AWS KMS Integration
Private key stored in AWS KMS can be used for signing a CPx.

### Requirements
The following environment variables need to be set up log in to AWS:
```shell
export AWS_ACCESS_KEY_ID=<value>
export AWS_SECRET_ACCESS_KEY=<value>
export AWS_SESSION_TOKEN=<value>
```
AWS Region has to be set in an AWS profile (`~/.aws/config`), e.g. `region = eu-west-2`.

### Sign CPx with AWS KMS key
```shell
./corda-cli.sh package sign-with-kms \
    mycpb.cpb \
    --file signed.cpb \
    --key-id edb939dd-7e2f-4624-a691-f6f9c8f74aaa \
    --cert-file cert.pem
```

### Build a CPB and sign with AWS KMS key
```shell
./corda-cli.sh package create-cpb-signed-with-kms \
    mycpk0.cpk mycpk1.cpk \
    --cpb-name manifest-attribute-cpb-name \
    --cpb-version manifest-attribute-cpb-version \
    --file output.cpb \
    --key-id edb939dd-7e2f-4624-a691-f6f9c8f74aaa \
    --cert-file cert.pem
```

### Create a CSR for a KMS signing key
AWS CLI doesn't provide an option to generate a CSR for a KMS signing key. To be able to request a signing certificate,
CSR needs to be created which can then be sent to a Certificate Authority who generates the signing certificate.
User can then use this certificate to sign a CPx with the above commands.
Alternatively self-signed certificate can also be used.
```shell
./corda-cli.sh package create-csr-for-kms-key \
    --csr-file csr-file.csr \
    --key-id edb939dd-7e2f-4624-a691-f6f9c8f74aaa \
    --common-name R3-Preview \
    --organizational-unit Engineering \
    --organization R3-test \
    --locality London \
    --state London \
    --country GB \
    --mail test@mail.uk
```

### Verification
Verification is done in a standard way using `corda-cli` or `jarsigner` mentioned in the previous section.