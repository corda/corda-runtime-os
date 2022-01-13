The keystore files used for the Gateway integration tests have been created using www.tinycert.org in order to have a working
OCSP responder.

# Overview of existing keystores

- sslkeystore_alice.jks
    - status: OK
    - CN: Alice
    - Alternates: www.alice.net, alice.net
- sslkeystore_bob.jks
    - status: REVOKED
    - CN: Bob
    - Alternates: www.bob.net, bob.net

- sslkeystore_chip.jks
    - status: OK
    - CN: Chip
    - Alternates: www.chip.net, chip.net, 127.0.0.1, https://127.0.0.1:10003

- sslkeystore_dale.jks
    - status: OK
    - CN: Dale
    - Alternates: www.dale.net, dale.net, 127.0.0.1, https://127.0.0.1:10004

# How to create new truststores and keystores

## Create the CA and certificates

1. Create a new CA using the tinycert dashboard.
2. Download the CA certificate. File will be .pem format and is named *cacert.pem*
3. Create a new certificate using the tintycert dashboard. Fill in the form, ensuring to specify at least one Subject Alternate Name.
4. Download the newly created certificate chain and private key. Files will be .pem format and are named *certchain.pem*
and *key.dec.pem* if it's clear text or *key.enc.pem* if it's encrypted.

## Create truststore.jks

1. Create an empty truststore. The initial entry will be deleted after in preparation for importing the CA certificate

```
keytool -genkey -keyalg RSA -alias temp -keystore truststore.jks
keytool -delete -alias temp -keystore truststore.jks
```

2. Import the CA into the truststore

```
keytool -import -v -trustcacerts -alias root -file cacert.pem -keystore truststore.jks 
```

## Create sslkeystore.jks

1. Create an empty keystore. The initial entry will be deleted after in preparation for importing the CA certificate

```
keytool -genkey -keyalg RSA -alias cordaclienttls -keystore sslkeystore.jks
keytool -delete -alias cordaclienttls -keystore sslkeystore.jks
```

2. Concatenate the certificate and private key

```
cat certchain.pem key.dec.pem > combined.pem
```

3. Convert the combined certificate and key from .pem to PKCS12

```
openssl pkcs12 -export -out combined.pkcs12 -in combined.pem
```

3. Import the combined certificate and key into the keystore

```
keytool -v -importkeystore -srckeystore combined.pkcs12 -srcstoretype PKCS12 -destkeystore sslkeystore.jks -deststoretype JKS

```