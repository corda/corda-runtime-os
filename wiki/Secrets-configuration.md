# SmartConfig

`SmartConfig` is an extension of [Typesafe Config](https://github.com/lightbend/config) that allows any configuration
elements to be treated as a "secret".
To configure an element as "secret", convert the value to a Config section under the `configSecret` key. The metadata needed to resolve or fetch the secret value is listed under this key.
Because this Config Section is unstructured, we can support multiple ways of resolving the secret by
providing an implementation of `SecretsLookupService`.
An advantage of this approach is that any configuration value can be encrypted and the consumer of the
configuration does not need to be aware. Operators can define their own policy about what is 
sensitive and what is not. For example, one could consider only passwords, such as DB passwords, 
sensitive or one could treat usernames as sensitive also. Corda does not have a preferred policy.

# EncryptionSecretsService

The _default_ implementation of `SecretsLookupService` is provided by the `EncryptionSecretsService` implementation.
This implementation uses symmetric encryption with a key that is derived from a _salt_ and _passphrase_ that are passed
to the worker processes as start-up parameters during the bootstrap process.
All processes that share a configuration must be configured with the same _salt_ and _passphrase_.
This offers some level of protection by ensuring that the secrets with configuration at rest are not readable unless the 
passphrase and salt are know. 
However, given that the passphrase and salt are passed into the worker processes as start-up parameters, it is possible
that they will leak into deployment scripts, for example, and be present along with other configuration that is passed
to the process at start-up (e.g. DB connection details for the DB worker).

Implementations that provide integration with 3rd party secrets managers such 
[HashiCorp Vault](https://www.vaultproject.io/) or [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/)
may be available in later releases and provide a more secure way of managing secrets.

# MaskedSecretsLookupService

The `MaskedSecretsLookupService` implemenation of `SecretsLookupService` has two roles. It acts as a fall-back when 
the `SmartConfigFactory` factory (`create`) method cannot configure a "real" `SecretsLookupService` implementation.
It can also be used as a way to ensure secrets are never revealed but always return `****`. This can be useful when
printing a configuration, for example.
You can create a copy of `SmartConfig` that always uses the `MaskedSecretsLookupService` by using the `toSafeConfig()` 
function.
This implementation is also the default when SmartConfig is _rendered_ (using the `root().render()` function).

# Use cases

An obvious use case for secrets is the DB credentials for the cluster DB which must be passed into the DB
workers at start-up.

Others are:

- DB credentials for other DBs (e.g. VNode Vault) stored in the DB cluster.
- Private keys managed by the Crypto Worker.

# Usage

## Boot

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

## Using a secret

Once `SmartConfig` has been configured correctly, fetching a secret is the same as fetching a regular `Config`
value. For example, `config.getString('foo')` returns the value for `foo` in plain text. 
You can also check whether a configuration value is a secret by using `config.isSecret('foo')`.
See `SmartConfigTest.kt` for more examples.

## Configuring a secret

To configure a secret, add a config section with the `configSecret` key and set the 
required metadata.
For the `EncryptionSecretsService` implementation, this is simply the encrypted value. 

For example:

```json
{
  "foo": "bar",
  "fred": "also"
}
```

`fred` can be turned into a secret as follows:

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

**Note:** This configuration can be a boot configuration or a configuration that is created using the Config
HTTP API, which will in turn be published to Kafka. As a result, it is important that sensitive configuration is not
persisted and published as plain text.

A `corda-cli` command will be available to easily create a secret configuration sections given a salt and passphrase.

## Creating secrets at runtime

In some cases, secrets need to be created and persisted by the workers. For example, the DB worker manages DB credentials for other DBs such as the VNode Vault DBs. In this case, the DB worker can use the `EncryptionSecretsService` implementation to create a Config Section from plain text using the 
`createValue(plainText: String)` function. Similarly, the Crypto Worker can use this to wrap keys and manage HSM configuration.

## SmartConfig validation with secrets present

Any configuration which has secrets must have entries defined within the equivalent JSON schema for the secret paths. However we do not expose the contents of the secret in the JSON schema, as such we do not validate the contents of the secret but we do validate that it is present or not. During the validation step of configs with secrets the validator will use a similar mechanism to the MaskedSecretsLookupService and replace the contents of the secret with a string. This string needs to be defined within the JSON schema in order for the config to pass validation. 

For example if there is a path with a secret present such as `fred.configSecret.encryptedSecret`. The JSON schema will need to have a string defined as allowed at the path `fred.configSecret`. Regardless of the depth and complexity of the config defined below the configSecret only a simple string is defined in the schema as this is all the validator will see due the secret being masked.
