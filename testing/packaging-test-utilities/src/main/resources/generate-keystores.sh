#!/bin/sh

# This script generates the development CorDapp signing key
# in src/main/resources/certificates.
#
# Note: It generates the keystore from scratch with a brand new key.

STOREPASS=cordadevpass
STORETYPE=pkcs12
KEYPASS=${STOREPASS}

# Root CA
keytool -genkeypair -keyalg EC -alias rootca -dname 'CN=Corda Dev Root CA, OU=R3, O=Corda, L=Dublin, C=IE' \
        -validity 3650 -keypass ${KEYPASS} -keystore rootca.p12 -storetype ${STORETYPE} -storepass ${STOREPASS} \
        -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign
# Intermediate CA 1
keytool -genkeypair -keyalg EC -alias ca1 -dname 'CN=Corda Dev CA1, OU=R3, O=Corda, L=Dublin, C=IE' \
        -validity 3650 -keypass ${KEYPASS} -keystore ca1.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}
# Intermediate CA 2
keytool -genkeypair -keyalg EC -alias ca2 -dname 'CN=Corda Dev CA2, OU=R3, O=Corda, L=Dublin, C=IE' \
        -validity 3650 -keypass ${KEYPASS} -keystore ca2.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}
# Alice
keytool -genkeypair -keyalg EC -alias alice -dname 'CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE' \
        -validity 3650 -keypass ${KEYPASS} -keystore alice.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}
# Bob
keytool -genkeypair -keyalg EC -alias bob -dname 'CN=Bob, OU=R3, O=Corda, L=Dublin, C=IE' \
        -validity 3650 -keypass ${KEYPASS} -keystore bob.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}

# Export root CA
keytool -exportcert -alias rootca -rfc -keystore rootca.p12 -storepass ${STOREPASS} > rootca.pem

# Import CA1's certificate signed by root CA
keytool -certreq -alias ca1 -keystore ca1.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias rootca -rfc -keystore rootca.p12 -storepass ${STOREPASS} \
                  -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign > ca1.pem
cat rootca.pem ca1.pem > ca1chain.pem
keytool -importcert -alias ca1 -file ca1chain.pem -noprompt -keystore ca1.p12 -storepass ${STOREPASS}

# Import CA2's certificate signed by root CA
keytool -certreq -alias ca2 -keystore ca2.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias rootca -rfc -keystore rootca.p12 -storepass ${STOREPASS} \
                  -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign > ca2.pem
cat rootca.pem ca2.pem > ca2chain.pem
keytool -importcert -alias ca2 -file ca2chain.pem -noprompt -keystore ca2.p12 -storepass ${STOREPASS}

# Import Alice's certificate signed by CA1
keytool -certreq -alias alice -keystore alice.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias ca1 -rfc -keystore ca1.p12 -storepass ${STOREPASS} \
                  -ext BC:critical=ca:false -ext KeyUsage=digitalSignature -ext ExtendedKeyUsage=codeSigning > alice.pem
cat rootca.pem ca1.pem alice.pem > alicechain.pem
keytool -importcert -alias alice -file alicechain.pem -noprompt -keystore alice.p12 -storepass ${STOREPASS}

# Import Bob's certificate signed by CA2
keytool -certreq -alias bob -keystore bob.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias ca2 -rfc -keystore ca2.p12 -storepass ${STOREPASS} \
                  -ext BC:critical=ca:false -ext KeyUsage=digitalSignature -ext ExtendedKeyUsage=codeSigning > bob.pem
cat rootca.pem ca2.pem bob.pem > bobchain.pem
keytool -importcert -alias bob -file bobchain.pem -noprompt -keystore bob.p12 -storepass ${STOREPASS}

# Clean up
rm rootca.pem ca1.pem ca1chain.pem ca2.pem ca2chain.pem alice.pem alicechain.pem bob.pem bobchain.pem