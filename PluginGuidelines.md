# Corda CLI Plugin Developer Guidelines
This document will give you a run through of the things you will need to be aware of when submitting a new Corda CLI Plugin. 

## Before Starting

### Command Structure
#### Syntax
The command structure for the CLI plugin should follow the ***Noun Verb*** command syntax. Where extra information is required, the information can be added as flags to both the noun and verb command parts.

A basic example of this would be targeting the already connected node and shutting it down:
`corda-cli node shutdown`

Targeting an unconnected cluster and counting virtual nodes:
`corda-cli cluster --url <example url> -u <cluster admin> -p <password> list-v-nodes --count`

Commands should be hyphenated not camelCase I.E. `list-v-nodes` not `listVNodes`.

#### Flags
Flags should be kept consistent with other plugins:
- `-p --password` Should always be password.
- `-u --user` Should always be user to auth.
- `-f --file` Should be used for any file path to be used.
- `-t --target-url` Should always be a target url.

#### Command Descriptions for Help
Always include command descriptions via PicoCli:
```kotlin
@Command(name="checksum", description="Prints the checksum (MD5 by default) of a file to STDOUT.")
```

### Development

### Branching
Development should be against the most recent version branch, not the main or release branches. Follow R3's branching best practices, start with Jira issue number, or no-tick.

### Tests
Please include unit testing or the PR will be declined. You can find example tests in the template and example plugins.
If possible also include an integration test, see the example plugins for examples. 

### Merging
You will need to raise a PR that includes a description of the work, and this will have to include one of the CLI team as a reviewer. 

## Template & Examples

Please check out the [template plugin that is in the CLI repo](https://github.com/corda/corda-cli-plugin-host/tree/main/plugins). You can use this as a base to begin development. You may also make use of the two example plugins included, one shows examples on how to use the CLI's built in services, e.g. the Http service.
