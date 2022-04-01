A tool that can act as a fake Certificate Authority to create TLS certificates (without revocation)

To build, run:
```bash
./gradlew :applications:tools:p2p-test:fake-ca:install
```

To create an authority run:
```bash
./applications/tools/p2p-test/fake-ca/build/install/fake-ca/bin/fake-ca create-ca 
```
This will save the CA details into `~/.fake.ca/ca/.ca/` (this can be changed using the `-m` option)

To run certificates run:
```bash
./applications/tools/p2p-test/fake-ca/build/install/fake-ca/bin/fake-ca add-certificate alice.com www.alice.com add-certificate www.bob.net bob.net 
```
This will use the saved details from `~/.fake.ca/ca/.ca/` (this can be changed using the `-m` option).
By default, the keys will be generated using the [EC](https://en.wikipedia.org/wiki/Elliptic_curve) algorithm, use the `-a` option to change algorithm.


