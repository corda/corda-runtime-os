# Package Plugin

This is a plug-in for [Corda CLI plug-in host](https://github.com/corda/corda-cli-plugin-host) for working with CPB and CPI files.

```shell
# These commands assume you have access to ./corda-cli.sh
cd script

# Generate two self-signed signing keys to show how we select one key among multiple
keytool -genkey -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000
keytool -genkey -alias "signing key 2" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 2, o=R3, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000

# To trust freetsa.org, download their CA cert and import into keystore
keytool -import -alias "freetsa" -keystore signingkeys.pfx -storepass "keystore password" -file ~/Downloads/cacert.pem

# Build a CPB
./corda-cli.sh package create-cpb \
    mycpk0.cpk mycpk1.cpk \
    --cpb-name manifest-attribute-cpb-name \
    --cpb-version manifest-attribute-cpb-version \
    --file output.cpb \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr

# Check CPB jarsigner signatures
jarsigner -keystore signingkeys.pfx -storepass "keystore password" -verbose -certs  -verify output.cpb

# Build a group policy file
./corda-cli.sh mgm groupPolicy > TestGroupPolicy.json

# Build a CPI
./corda-cli.sh package create \
    --cpb mycpb.cpb \
    --group-policy TestGroupPolicy.json \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr

# Pipe group policy into CPI
./corda-cli.sh mgm groupPolicy | ./corda-cli.sh package create \
    --cpb mycpb.cpb \
    --group-policy - \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr
    
# Build a CPI v2
./corda-cli.sh package create-cpi \
    --cpb mycpb.cpb \
    --group-policy TestGroupPolicy.json \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr    
    
# Pipe group policy into CPI v2
./corda-cli.sh mgm groupPolicy | ./corda-cli.sh package create-cpi \
    --cpb mycpb.cpb \
    --group-policy - \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr
 
# Check CPI jarsigner signatures
jarsigner -keystore signingkeys.pfx -storepass "keystore password" -verbose -certs  -verify output.cpi

# Verify a CPI
./corda-cli.sh package verify mycpi.cpi

# Sign a CPI or CPB or CPK
./corda-cli.sh package sign \
    mycpb.cpb \
    --file signed.cpb \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr

# Check a CPI or CPB or CPK jarsigner signatures
jarsigner -keystore signingkeys.pfx -storepass "keystore password" -verbose -certs  -verify signed.cpb
```
