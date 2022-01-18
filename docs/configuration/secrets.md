# Configuration of Secrets

## SmartConfig

SmartConfig is an extension of [Typesafe Config](https://github.com/lightbend/config) that allows for any configuration
elements to be treated as a "secret".
This is done by converting the value to a Config section under the `configSecret` key to identify
it as a secret, the metadata needed to resolve or fetch the secret value is listed under this key.
Because this Config Section is unstructured, we can support multiple ways of resolving the secret. This is done by
providing an implementation of `SecretsLookupService`.
An advantage of this approach is that any configuration value can be encrypted and that the consumer of this
configuration does not need to be aware. This means that operators can define their own policy about which is 
sensitive configuration and which isn't. For example, one could only consider passwords, such as DB passwords, 
sensitive, or one could treat usernames as secrets as well. Corda is not opinionated about this policy.

## EncryptionSecretsService

The _default_ implementation of `SecretsLookupService` is provided by the `EncryptionSecretsService` implementation.
This implementation used symmetric encryption with a key that is derived from a _salt_ and _passphrase_ that are passed
to the worker processes as start-up parameters during the bootstrap process.
This means all processes that share a configuration need to be configured with the same _salt_ and _passphrase_.
This offers some level protection by ensuring that the secrets with configuration at rest is not readable unless the 
passphrase and salt are know. 
However, given that the passphrase and salt are passed into the worker processes as start-up parameters, it is possible
that they will leak into deployment scripts, for example, and be present along with other configuration that is passed
to the process at start-up (e.g. DB connection details for the DB worker).

Implementations that provide integration with 3rd party secrets managers such 
[HashiCorp Vault](https://www.vaultproject.io/) or [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/)
may be available in later releases and provide a more secure way of managing secrets.

## MaskedSecretsLookupService

The `MaskedSecretsLookupService` implemenation of `SecretsLookupService` has 2 roles. It acts as a fall-back when 
the SmartConfigFactory factory (`create`) method cannot configure a "real" `SecretsLookupService` implementation.
It can also be used as a way to ensure secrets are never revealed, but always return `****`. This can be useful when
printing a configuration, for example.
You can create a copy `SmartConfig` that always uses the `MaskedSecretsLookupService` by using the `toSafeConfig()` 
function.
This implementation is also the default when SmartConfig is _rendered_ (using the `root().render()` function).

## Use cases

An obvious use case for secrets is the DB credentials for the cluster DB which need to be passed into the DB
workers at start-up.

Others are:

- DB credentials for other DBs (e.g. VNode Vault) stored in the DB cluster.
- Private keys managed by the Crypto Worker.

## Usage

### Boot

Workers are configured to use the `EncryptionSecretsService` by passing in the following parameters:

```bash
<processor> -s salt=<salt> -s passphrase=<passphrase>
```

The start-up parameters are purposely "unstructured", as other implementations of `SecretsLookupService` may require 
different configuration.

Given the above parameters, the process will create a `SmartConfigFactory` like this:

```kotlin
val secretsConfig = ConfigManager.parseMap(<map-from-values-passed-into-the-s-startup-parameter>)
val configFactory = SmartConfigFactory.create(secretsConfig)
```

A regular typesafe `Config` object can then be turned into a `SmartConfig` object like so:

```kotlin
val config = configFactory.create(<type-safe-config-object>)
```

See `applications/workers/release/deploy/docker-compose.yaml` for an example of an encrypted DB credential passed into
the DB worker.

### Using a secret

Once the `SmartConfig` has been configured correctly, fetching a secret is the same as fetching a regular `Config`
value. E.g. `config.getString('foo')` will return the value for `foo` in plain text. 
You can also check whether a configuration value is a secret by using `config.isSecret('foo')`.
See `SmartConfigTest.kt` for more examples.

### Configuring a secret

Configuring a secret is done by adding a config section with the `configSecret` key and setting the 
required metadata.
For the `EncryptionSecretsService` implementation, this is simply the encrypted value. 

For example:

```json
{
  "foo": "bar",
  "fred": "also"
}
```

`fred` can be turned into a secret like so:

```json
{
  "foo": "bar",
  "fred": {
    "configSecret": {
      "encryptedSecret": "<encrypted-value>"
    } 
  }
}
```

Note that this configuration can be boot configuration, or it could be configuration that is created using the Config
HTTP API, which will in turn be published to Kafka. This is why it is important that sensitive configuration is not
persisted and published as plain text.

A `corda-cli` command will be available (sub command of `config`?) to easily create a secret configuration sections 
given a salt and passphrase.

### Creating secrets at runtime

In some cases, secrets will need to be created and persisted by the workers. For example, the DB worker manages
DB credentials for other DBs such as the VNode Vault DBs. In this case, the DB worker can use the 
`EncryptionSecretsService` implementation to create a Config Section from plain text using the 
`createValue(plainText: String)` function.
Similarly, the Crypto Worker my need to use this to wrap keys and manage HSM configuration.
