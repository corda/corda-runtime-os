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

# Alice version 1
keytool -genkeypair -keyalg EC -alias alice -dname 'CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE, OID.1.3.6.1.4.1.311.60.2.1.3=GB, OID.2.5.4.15=Private, SERIALNUMBER=10103259' \
        -validity 3650 -keypass ${KEYPASS} -keystore alice.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}
# Alice version 2 (same X500 attributes as above but different SERIALNUMBER)
keytool -genkeypair -keyalg EC -alias alice -dname 'CN=Alice, OU=R3, O=Corda, L=Dublin, C=IE, OID.1.3.6.1.4.1.311.60.2.1.3=GB, OID.2.5.4.15=Private, SERIALNUMBER=10103258' \
        -validity 3650 -keypass ${KEYPASS} -keystore alice-v2.p12 -storetype ${STORETYPE} -storepass ${STOREPASS}

# Export root CA
keytool -exportcert -alias rootca -rfc -keystore rootca.p12 -storepass ${STOREPASS} > rootca.pem

# Import CA1's certificate signed by root CA
keytool -certreq -alias ca1 -keystore ca1.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias rootca -rfc -keystore rootca.p12 -storepass ${STOREPASS} -validity 3650 \
                  -ext BasicConstraints:critical -ext KeyUsage=cRLSign,digitalSignature,keyCertSign > ca1.pem
cat rootca.pem ca1.pem > ca1chain.pem
keytool -importcert -alias ca1 -file ca1chain.pem -noprompt -keystore ca1.p12 -storepass ${STOREPASS}

# Import Alice's certificate signed by CA1
keytool -certreq -alias alice -keystore alice.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias ca1 -rfc -keystore ca1.p12 -storepass ${STOREPASS} -validity 3650 \
                  -ext BC:critical=ca:false -ext KeyUsage=digitalSignature -ext ExtendedKeyUsage=codeSigning > alice.pem
cat rootca.pem ca1.pem alice.pem > alicechain.pem
keytool -importcert -alias alice -file alicechain.pem -noprompt -keystore alice.p12 -storepass ${STOREPASS}

# Import Alice's version 2 certificate signed by CA1
keytool -certreq -alias alice -keystore alice-v2.p12 -storepass ${STOREPASS} \
        | keytool -gencert -alias ca1 -rfc -keystore ca1.p12 -storepass ${STOREPASS} -validity 3650 \
                  -ext BC:critical=ca:false -ext KeyUsage=digitalSignature -ext ExtendedKeyUsage=codeSigning > alice-v2.pem
cat rootca.pem ca1.pem alice-v2.pem > alicechain-v2.pem
keytool -importcert -alias alice -file alicechain.pem -noprompt -keystore alice.p12 -storepass ${STOREPASS}

# Clean up
rm rootca.pem rootca.p12 ca1.pem ca1chain.pem ca1.p12 alice.pem alicechain.pem alice-v2.pem alicechain-v2.pem