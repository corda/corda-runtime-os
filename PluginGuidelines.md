# Corda CLI Plugin Developer Guidelines
This document will give you a run through of the things you will need to be aware of when submitting a new Corda CLI Plugin. Please ensure you have read this before you begin development. Thank you.

## Before Starting
### Template
Before you being development, please check out the [template plugin that is in the CLI repo](https://github.com/corda/corda-cli-plugin-host/tree/main/plugins). You can use this as a base to begin development. You may also make use of the two example plugins included, one shows examples on how to use the CLI's built in services, e.g. the Http service.

### Command Structure
#### Syntax
The command structure for the CLI plugin should follow the ***Noun Verb*** command syntax. Where extra information can be added to both command parts.

A basic example of this would be targeting the already connected node and shutting it down:
`corda-cli node shutdown`

or targeting an unconnected cluster and counting virtual nodes:
`corda-cli cluster --url <example url> -u <cluster admin> -p <password> list-v-nodes --count`

#### Flags
flags should be kept consistent with other plugins:
`-p --password` should always be password
`-u --user` should always be user to auth.
`--url` should always be a target url.

#### Case
commands should be hyphenated not camelCase
`list-v-nodes` vs `listVNodes`

#### Command Descriptions for Help
Always include command descriptions via PicoCli:
```kotlin
@Command(name="checksum", description="Prints the checksum (MD5 by default) of a file to STDOUT.")
```

### Development

### Branching
please develop against the most recent version branch, not main or release branches. Follow R3's branching best practices, start with Jira ticket no, or no-tick.

### Writing to Files
If you plugin writes to files, please make sure you are writing to one of the following:
`~/.corda/cli`,
`./files`, - need a better location
or a workspace on Jenkins etc

### Tests
Please include unit testing or the PR will be declined. You can find example tests in the template and example plugins.
*need confirmation for this next part*
Please include integration tests using python script to test your commands if possible (network interaction is an exception)

### Merging
You will need to raise a PR that includes a description of the work, and this will have to include one of the CLI team as a reviewer. 