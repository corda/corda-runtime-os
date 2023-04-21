The keystore files used for the p2p integration tests have been created using www.tinycert.org in order to have a working
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
- sslkeystore_127.0.0.1.jks
    - status: OK
    - CN: 127.0.0.1
    - Alternates: 127.0.0.1 (as an IP not a DNS name)

For sslkeystore_alice.jks and sslkeystore_bob.jks we also downloaded the certificates into certificate_alice.pem and certificate_bob.pem respectively.

The sslkeystore_c5.jks store contains the following subject alternate names. It is used for the HostnameMatcher unit tests.
```
SubjectAlternativeName [
DNSName: www.test.com
DNSName: www.test.co.uk
DNSName: *.test.net
IPAddress: 10.11.12.13
IPAddress: 2001:db8:3333:4444:cccc:dddd:eeee:ffff
IPAddress: 2001:db8:3c4d:15:0:0:1a2f:1a2b
]
```

# How to create new truststores and keystores
## With support of revocation (using tinycert)

TinyCert provides CRL and OCSP endpoints and an easy way to revoke certificates via their web interface, so it can be used in scenarios where certification revocation needs to be tested. However, it provides only the option of creating RSA certificates.
### Create the CA and certificates

1. Create a new CA using the tinycert dashboard.
2. Download the CA certificate. File will be .pem format and is named *cacert.pem*
3. Create a new certificate using the tintycert dashboard. Fill in the form, ensuring to specify at least one Subject Alternate Name.
4. Download the newly created certificate chain and private key. Files will be .pem format and are named *certchain.pem*
and *key.dec.pem* if it's clear text or *key.enc.pem* if it's encrypted.

### Create truststore.pem

1. Copy (or rename) the cacert.pem to  truststore.pem

```
cp cacert.pem truststore.pem 
```

### Create sslkeystore.jks

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

## Without support of revocation using openSsl
openssl allows for better automation of the certificate generation process without having to use third-party systems. It also allows using different algorithms (e.g. RSA and ECDSA) for the keys. However, it is not as straightforward to perform revocation of certificates.
### Create the CA and certificates
1. Create a new empty directory and change dir to it:
```bash
mkdir -p ~/.ca
cd ~/.ca
```
2. Prepare the CA configuration
```bash
mkdir -p ca.db.certs   # Signed certificates storage
touch ca.db.index      # Index of signed certificates
echo 01 > ca.db.serial # Next (sequential) serial number
cat>ca.conf<<'EOF'
[ ca ]
default_ca = ca_default

[ ca_default ]
dir = REPLACE_LATER
certs = $dir
new_certs_dir = $dir/ca.db.certs
database = $dir/ca.db.index
serial = $dir/ca.db.serial
RANDFILE = $dir/ca.db.rand
certificate = $dir/ca.crt
private_key = $dir/ca.key
default_days = 1024
default_crl_days = 1024
default_md = md5
preserve = no
policy = generic_policy
copy_extensions = copy
[ generic_policy ]
countryName = optional
stateOrProvinceName = optional
localityName = optional
organizationName = optional
organizationalUnitName = optional
commonName = supplied
emailAddress = optional

EOF

sed -i "s|REPLACE_LATER|$(pwd)|" ca.conf
```

3. Generate the CA key:
  * EC:
```bash
openssl ecparam -out ca.key -name prime256v1 -genkey
```
  * RSA:
```bash
openssl genrsa -out ca.key 2048
```

4. Generate CA certificate
```bash
openssl req -new -x509 -nodes -key ca.key -out cacert.pem -passin "pass:password" -passout "pass:password" -subj "/C=UK/CN=r3.com"
```

### Create Trust store
To create the trust store run:
```bash
cp cacert.pem truststore.pem
```
This will create the trust store in `./truststore.pem`

### Create SSL key store
1. Generate the key for `<name>`:
  * EC:
```bash
openssl ecparam -out <name>.key -name prime256v1 -genkey
```
  * RSA:
```bash
openssl genrsa -out <name>.key 2048
```

2. Signing request for `<name>` with `<url>`:
```bash
openssl req -new -key <name>.key -out <name>.csr -subj "/C=UK/CN=<url>" -addext "subjectAltName = DNS:<url>"
```
(for example:
```bash
openssl req -new -key alice.key -out alice.csr -subj "/C=UK/CN=www.alice.net" -addext "subjectAltName = DNS:www.alice.net"
```
)

3. Create certificate from the request
```bash
openssl ca -in <name>.csr -out <name>.cer -cert cacert.pem -keyfile ca.key -passin "pass:password" -config ca.conf -batch -passin "pass:password" -md sha512
```
4. Create the key store for `<name>`
```bash
cat <name>.cer <name>.key > <name>.combined.pem
openssl pkcs12 -export -out <name>.combined.pkcs12 -in <name>.combined.pem -passin "pass:password" -passout "pass:password"
keytool -v -importkeystore -srckeystore <name>.combined.pkcs12 -srcstoretype PKCS12 -destkeystore <name>.jks -deststoretype JKS -srcstorepass password -deststorepass password -noprompt
```

This should create a key store named `<name>.jks`

Repeat the same process for every key store that uses the same trust store