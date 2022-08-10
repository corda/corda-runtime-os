#!/bin/sh

STOREPASS="keystore password"
KEY_ALG=EC
SIGNING_KEY_1="signing key 1"
SIGNING_KEY_2="signing key 2"

# Root CA
keytool -genkeypair -alias rootca -dname 'CN=Corda Dev Root CA, OU=R3, O=Corda, L=Dublin, C=IE' \
        -keyalg ${KEY_ALG} -validity 4000 -keystore signingkeys.pfx -storetype pkcs12 -storepass "${STOREPASS}" \
        -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign

keytool -genkeypair -alias "${SIGNING_KEY_1}" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, c=GB" \
        -keyalg ${KEY_ALG} -validity 4000 -keystore signingkeys.pfx -storetype pkcs12 -storepass "${STOREPASS}"

keytool -genkeypair -alias "${SIGNING_KEY_2}" -dname "cn=CPI Plugin Example - Signing Key 2, o=R3, c=GB" \
        -keyalg ${KEY_ALG} -validity 4000 -keystore signingkeys.pfx -storetype pkcs12 -storepass "${STOREPASS}" \

# Export root CA
keytool -exportcert -alias rootca -rfc -keystore signingkeys.pfx -storepass "${STOREPASS}" > rootca.pem
# Import root CA to keystore
keytool -importcert -alias rootca-cert -file rootca.pem -noprompt -keystore signingkeys.pfx -storepass "${STOREPASS}"

# Import signing key 1 certificate signed by root CA
keytool -certreq -alias "${SIGNING_KEY_1}" -keystore signingkeys.pfx -storepass "${STOREPASS}" \
        | keytool -gencert -alias rootca -rfc -keystore signingkeys.pfx -storepass "${STOREPASS}" \
                  -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign > key1.pem
cat rootca.pem key1.pem > key1-chain.pem
keytool -importcert -alias "${SIGNING_KEY_1}" -file key1-chain.pem -noprompt -keystore signingkeys.pfx -storepass "${STOREPASS}"

# Import signing key 2 certificate signed by root CA
keytool -certreq -alias "${SIGNING_KEY_2}" -keystore signingkeys.pfx -storepass "${STOREPASS}" \
        | keytool -gencert -alias rootca -rfc -keystore signingkeys.pfx -storepass "${STOREPASS}" \
                  -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign > key2.pem
cat rootca.pem key2.pem > key2-chain.pem
keytool -importcert -alias "${SIGNING_KEY_2}" -file key2-chain.pem -noprompt -keystore signingkeys.pfx -storepass "${STOREPASS}"

rm rootca.pem
rm key1.pem
rm key1-chain.pem
rm key2.pem
rm key2-chain.pem
