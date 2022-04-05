# Package Plugin

Plugin for working with CPI files.

```shell
# These commands assume you are in the script folder to have access to ./corda-cli.sh
cd script

# Generate two self-signed signing keys to show how we select one key among multiple
keytool -genkey -alias "signing key 1" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 1, o=R3, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000
keytool -genkey -alias "signing key 2" -keystore signingkeys.pfx -storepass "keystore password" -dname "cn=CPI Plugin Example - Signing Key 2, o=R3, c=GB" -keyalg RSA -storetype pkcs12 -validity 4000

# To trust freetsa.org, download their CA cert and import into keystore
keytool -import -alias "freetsa" -keystore signingkeys.pfx -storepass "keystore password" -file ~/Downloads/cacert.pem

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

# Pipe group policy into application package
./corda-cli.sh mgm groupPolicy | ./corda-cli.sh package create \
    --cpb mycpb.cpb \
    --group-policy - \
    --file output.cpi \
    --keystore signingkeys.pfx \
    --storepass "keystore password" \
    --key "signing key 1" \
    --tsa https://freetsa.org/tsr
 
# Check jarsigner signatures
jarsigner -keystore signingkeys.pfx -storepass "keystore password" -verbose -certs  -verify output.cpi

# Verify a CPI
./corda-cli.sh package verify mycpi.cpi
```
