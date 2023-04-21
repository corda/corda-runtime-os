# Secret Config plug-in

This plug-in uses the encrypting secrets service from Corda5 runtime to secure config values.

```
Usage: secret-config [-p=<passphrase>] [-s=<salt>] <value> [COMMAND]
    Handle secret config values given the value, a passphrase and a salt
        <value>         The value to secure for configuration
        -p, --passphrase=<passphrase> 
                    Passphrase for the encrypting secrets service
        -s, --salt=<salt>   Salt for the encrypting secrets service
    Commands:
        create   Create a secret config value given a salt and a passphrase
        decrypt  Decrypt a secret value given salt and passphrase (takes the actual
                 value, not the config)
```

Salt and passphrase are required inputs.

In create mode, the ouput is a secret config value with the correct JSON bits around it so it can 
be pasted straight into a HOCON file

In decrypt mode, the expected input is just the encrypted string, it does not do config parsing at all.
