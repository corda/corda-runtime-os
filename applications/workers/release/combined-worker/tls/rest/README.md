This directory contains PKCS12 keystore which will be used for TLS certificate by REST worker.

[OpenSSL tool](https://www.openssl.org/) is required to be able to re-generate this certificate from scratch.

    # Generate CA certificate
    openssl req -x509 -sha256 -newkey rsa:4096 -keyout ca.key -out ca.crt -days 10000 -nodes -subj '/CN=Combined Worker Certificate Authority'

    # Generate CSR for server certificate
    openssl req -new -newkey rsa:4096 -keyout server.key -out server.csr -nodes -subj '/CN=localhost'

    # Process CSR and issue a certificate
    openssl x509 -req -sha256 -days 1000 -in server.csr -CA ca.crt -CAkey ca.key -out server.crt -CAcreateserial

    # Pack certificate chain into a key store along with a private key
    openssl pkcs12 -export -out rest_worker.pfx -name rest_worker_entry -inkey server.key -in server.crt -certfile ca.crt -passout 'pass:mySecretPassword'

    # Tidy-up
    rm ca.* server.key server.csr server.crt