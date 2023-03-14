# SSL Certificate Read Implementation

Contains implementation of SSL Certificate Read service.

## Generating the self-signed HTTPS Keystore

The following command was used to create the `https.keystore` in this module's `resources` directory:

```shell
keytool -genkey -alias rest-api -dname "CN=r3.com,O=R3 Limited,C=GB" -keyalg RSA -keystore https.keystore -storepass httpsPassword -validity 1000
```

This created `https.keystore` in the directory the command was run in, which was then copied to the `resources` directory.