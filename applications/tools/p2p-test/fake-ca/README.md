A tool that can as a fake Certificate Authority to create local TLS certificates (without revocation).

To build, run:
```bash
./gradlew :applications:tools:p2p-test:fake-ca:install
```

To run use:
```bash
./applications/tools/p2p-test/fake-ca/build/install/fake-ca/bin/fake-ca create alice.com www.alice.com create www.bob.net bob.net 
```
By default, the keys will be generated using the [EC](https://en.wikipedia.org/wiki/Elliptic_curve) algorithm, use the `-a` option to change algorithm.
All the certificates and keys will be saved into `~/.fake.ca/`, use the `-m` option to save them to another location.

