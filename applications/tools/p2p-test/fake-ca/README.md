# Fake Certificate Authority tool
A tool that can act as a fake Certificate Authority to create TLS certificates (without revocation)

**⚠ WARNING:** This tool is not safe for production use, and it should only be used for testing purposes.

## Building the tool

To build, run:
```bash
./gradlew :applications:tools:p2p-test:fake-ca:clean :applications:tools:p2p-test:fake-ca:appJar
```
This will create a executable JAR fild in `applications/tools/p2p-test/fake-ca/build/bin`.

## Running the tool

To create an authority run:
```bash
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca* create-ca 
```
This will save the CA details into `~/.fake.ca/ca/.ca/` (this can be changed using the `-m` option)

To run certificates run:
```bash
java -jar ./applications/tools/p2p-test/fake-ca/build/bin/corda-fake-ca* create-certificate alice.com www.alice.com create-certificate www.bob.net bob.net 
```
This will use the saved details from `~/.fake.ca/ca/.ca/` (this can be changed using the `-m` option).
By default, the keys will be generated using the [EC](https://en.wikipedia.org/wiki/Elliptic_curve) algorithm, use the `-a` option to change algorithm.


