# Fake Certificate Authority tool
A tool that can act as a fake Certificate Authority to create TLS certificates (without the ability for revocation).

**⚠ WARNING:** This tool is not safe for production use, and it should only be used for testing purposes.

## Building the tool

To build the JAR artefact, run:
```bash
./gradlew :applications:tools:p2p-test:fake-ca:clean :applications:tools:p2p-test:fake-ca:appJar
```
This will create an executable JAR file in `applications/tools/p2p-test/fake-ca/build/bin`.

## Running the tool

### Creating a certificate authority
To create a certificate authority run:
```bash
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca*.jar create-ca 
```
This will store:
* the certificate of the certificate authority in PEM format under `~/.fake.ca/ca`
* internal details that can be used by the tool to reconstruct the CA under `~/.fake.ca/ca/.ca/` 
A custom directory can be specified instead of `~/.fake.ca` using the command line parameter `-m`.

### Creating a certificate

To create a TLS certificate run:
```bash
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca*.jar create-certificate alice.com www.alice.com
```
This will create a TLS certificate for the DNS names `alice.com`, `www.alice.com` and store the certificate and the keys under `.fake.ca/alice.com/`.
This requires a certificate authority to have been created first and it will attempt to load it from `~/.fake.ca/ca/.ca/` (this can be changed using the option `-m`).
By default, the generated keys will be [elliptic curve (EC) keys](https://en.wikipedia.org/wiki/Elliptic_curve). You can specify a different algorithm using the option `-a`.


