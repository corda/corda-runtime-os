# HTTP RPC Stub Implementations

Contains stub implementations of the HTTP RPC APIs for APIs that cannot currently have their read implementations created.

This module will be deleted once the real implementations are added.

## Generating the Stub SSL certificate

The following command was used to create the `cert.pem` in this module's `resources` directory:

```shell
openssl req -x509 -newkey rsa:4096 -keyout key.pem -out cert.pem -days 365
```

This then triggers a number of console input lines. The following content was used to generate the existing certificate:

```shell
Enter PEM pass phrase: Corda
Verifying - Enter PEM pass phrase: Corda
-----
You are about to be asked to enter information that will be incorporated
into your certificate request.
What you are about to enter is what is called a Distinguished Name or a DN.
There are quite a few fields but you can leave some blank
For some fields there will be a default value,
If you enter '.', the field will be left blank.
-----
Country Name (2 letter code) []:GB
State or Province Name (full name) []:
Locality Name (eg, city) []:London
Organization Name (eg, company) []:Corda
Organizational Unit Name (eg, section) []:
Common Name (eg, fully qualified host name) []:
Email Address []:
```

A `cert.pem` and `key.pem` are then output after running the command above.

The `cert.pem` was then copied to the `resources` directory.