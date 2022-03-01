# Corda CLI Host - Developer Guidelines for Writing Plugins

## Before Beginning Development

There is a [template plugin](https://github.com/corda/corda-cli-host-plugin-template) available for you to use as a
starting point. It contains the build.gradle needed to assemble and package the plugin. You can copy this repo and
change its remote origin to your own plugin git repository.

There are also two example plugins in this repo under the [plugins module](/plugins). These contain information on how
to import services from the host and how to write basic tests.

> **NOTE:** _**When importing the CordaCliPlugin API module in the `build.gradle` you must use `compileOnly` to avoid classpath clashes.**_

## Command Structure

### Syntax

The command structure for the CLI plugin should follow the ***Noun Verb*** command syntax. Where extra information is
required, the information can be added as flags to both the noun and verb command parts.

A basic example of this would be targeting the already connected node and shutting it down:
`corda-cli node shutdown`

Targeting an unconnected cluster and counting virtual nodes:
`corda-cli cluster --url <example url> -u <cluster admin> -p <password> list-v-nodes --count`

### Case

Commands and flags should be hyphenated, e.g. `list-v-nodes`. Please do not use camel case, e.g. `listVNodes`.

#### Flags

Flags can be included on either the noun or verb command part. Flags should be kept sensible and cosistent across your
plugin. E.g. if one command uses `-p --password` for then any other command in the plugin must use the same flag.

#### Recommended flags

These flags are recommended but not required.

- `-p --password` Should be any auth password.
- `-u --user` Should always be user to auth.
- `-f --file` Should be used for any input file path to be used.
- `-o --output-file` should be for any file to be written to.
- `-t --target-url` Should be a target url.

#### Command Descriptions for Help

Always include command descriptions via PicoCli:

```kotlin
@Command(name = "checksum", description = "Prints the checksum (MD5 by default) of a file to STDOUT.")
```

For production environments...

```sh
npm install --production
NODE_ENV=production node app
```

## Plugin Development

### Branching

If developing a plugin in a shared repo such as the plugin module of this repo, development should be against the most
recent version branch, not the main branch. Follow R3's branching best practices:

- Branch names should start with a Jira issue number, or NOTICK.
- Changes should be squashed and merged, with a sensible description added at this point.

### Tests

Please include unit testing or the PR will be declined. You can find example tests in the template and example plugins.
If possible also include an integration test, see the example plugins for examples.

### Merging

You will need to raise a PR that includes a description of the work, and this will have to include one of the CLI team
as a reviewer. 