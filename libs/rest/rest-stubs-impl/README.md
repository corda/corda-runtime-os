# Rest Stub Implementations

Contains stub implementations of the Rest APIs for APIs that cannot currently have their read implementations created.

This module will be deleted once the real implementations are added.

## Generating the Stub HTTPS Keystore

The following command was used to create the `https.keystore` in this module's `resources` directory:

```shell
keytool -genkey -alias http-api -dname "CN=r3.com,O=R3, C=GB" -keyalg RSA -keystore https.keystore -storepass httpsPassword
```

This created `https.keystore` in the directory the command was run in, which was then copied to the `resources` directory.