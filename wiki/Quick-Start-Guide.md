# Introduction
Corda 5 is the next generation of [R3's](http://www.r3.com) [digital ledger technology](https://www.corda.net/). 

We currently (as of 2022-04-26) do not ship or make publicly available all the object code (jar files, class files, etc).  As a result, if you want to write your own CorDapp using Corda 5, you must download and build from source.

# Corda 5 repositories
The code that implements Corda 5 and manages the building and deployment processes of a Corda 5 cluster is split across these repositories: 
* [corda/corda-runtime-os](https://github.com/corda/corda-runtime-os)
* [corda/corda-api](https://github.com/corda/corda-api)
* [corda/corda-cli-plugin-host](https://github.com/corda/corda-cli-plugin-host)
* [corda/corda-dev-helm (required for deployment)](https://github.com/corda/corda-dev-helm)

## Automated checkout script (optional)
The following script is provided for convenience but you may also manually check out the projects if preferred.
```
#!/bin/bash

mkdir corda
cd ./corda
git clone https://github.com/corda/corda-runtime-os.git 
git clone https://github.com/corda/corda-api.git 
git clone https://github.com/corda/corda-cli-plugin-host.git 
git clone https://github.com/corda/corda-dev-helm.git # Required for deployment
```

**Note:** To build the code successfully all cloned repositories should be immediate children of the same parent directory. 
In Corda 5 we have adopted [Gradle's composite build](https://docs.gradle.org/current/userguide/composite_builds.html) to simplify the overall building process for the end user. The composite build is a centralized build process where a Gradle task is triggered from one project and includes the other required builds (known as included builds).

See the [Building Corda 5](#building-corda-5) section for more details.

# Requirements

## Hardware 
Most of the computers that we use to develop, build, and test Corda 5 have:
* Gen 9 Intel (6 cores / 12 threads) CPUs
* 32GiB RAM
* At least 30GiB disk.

These are _not_ minimum specifications. This what is known to work with the code as of 2022-04-26, which is still under development. 

## Software
### Operating Systems

Corda 5 should run on and be developed on:
* MacOS
* Linux
* Windows 10/11

### Other Software
* [Java (Azul Zulu JDK 11)](https://www.azul.com/downloads/?version=java-11-lts&package=jdk) (**Note:** other versions should work but have not been extensively tested.)
* [Intellij ~v2021.X.Y community edition](https://www.jetbrains.com/idea/)
* git ~v2.24.1
* docker-engine ~v20.X.Y or docker-desktop ~v3.5.X 
* Kubernetes (incl. kubectl)
* [Helm](https://helm.sh/docs/intro/install/)

## Building Corda 5
To build, clone each repository in the same parent directory: 
* [corda/corda-runtime-os](https://github.com/corda/corda-runtime-os)
* [corda/corda-api](https://github.com/corda/corda-api)
* [corda/corda-cli-plugin-host](https://github.com/corda/corda-cli-plugin-host)

As the projects have been configured to use composite build, you only have to run a single build task from the corda-runtime-os project.
The composite build is based on the following:
* The boolean property ```CompositeBuild``` in the ```gradle.properties``` file in the root directory of the corda-runtime-os project. You must update this to true as it is set to false by default.
* The project/folder naming (as above). If for some reason the projects are named differently, the project name references can be updated in the same ```gradle.properties``` file. For example:

```
# Enables the substitution of binaries for source code if it exists in expected location
# Default behavior is false.
compositeBuild=false
cordaApiLocation=../corda-api
cordaCliHostLocation=../corda-cli-plugin-host
```

If you are intending to deploy using minikube, configure your shell to use the Docker daemon inside minikube so that built images are available directly to the cluster:

Bash:

```bash
eval $(minikube docker-env)
```

PowerShell:

```pwsh
minikube docker-env --shell=powershell | Invoke-Expression
```

From the ```<parent-dir>/corda-runtime-os``` directory run the following command:

```./gradlew publishOSGiImage```

## Deploying a local Corda 5 cluster
For information about deploying a local cluster, see [Local development with Kubernetes](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes) This section includes information about running end-to-end tests.