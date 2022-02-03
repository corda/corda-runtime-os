The keystore files used for the p2p e2e tests have been created using www.tinycert.org in order to have a working
OCSP responder.

# Overview of existing keystores

- sslkeystore_alice.jks
    - status: OK
    - CN: Alice
    - Alternates: www.alice.net, alice.net
    - RSA key

- sslkeystore_chip.jks
    - status: OK
    - CN: Chip
    - Alternates: www.chip.net, chip.net, 127.0.0.1, https://127.0.0.1:10003
    - RSA key

- receiver.jks
  - status: OK
  - CN: www.receiver.net
  - Alternates: www.receiver.net
  - ECDSA key

- sender.jks
  - status: OK
  - CN: www.sender.net
  - Alternates: www.sender.net
  - ECDSA key

For instructions on how to (re)create truststores/keystores, refer to the README file under the gateway integration tests [here](../../../../gateway/src/integrationTest/resources/README.md).