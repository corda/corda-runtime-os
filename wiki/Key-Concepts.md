# The installer

CorDapps are packaged in a single `.jar` file called CorDapp Package Installer (CPI) containing all the pieces required to join and participate in an application network:

* The location of the network operator.
* A list of membership requirements.
* Third party dependencies.
* CorDapp logic.

## CorDapp Packages (`.cpk`s)
The building blocks of these applications are a new file format called CorDapp Packages (.`cpk`s). This includes workflow and contract packages, additional metadata, a dependency tree and version information. You can independently version `.cpk`s. Each .`cpk` runs in its own sandbox, isolated from other CPKs. This prevents dependency clashes and facilitates faster CorDapp development.

## CorDapp Package Bundles (`.cpb`s)
The application publisher brings the individual `.cpk` files together to make a single CorDapp Package Bundle (`.cpb`). The application publisher is a single entity that coordinates multiple parties to create a single application bundle for a network. When multiple firms compose CorDapps together, it creates a strong technical dependency that facilitates development, distribution, and upgrades.

The application publisher adds the information about the network and the file becomes a CorDapp Package Installer (`.cpi`), which can be distributed to new members to begin onboarding and to existing members to perform upgrades.

The only difference between a development and a production CPI is the network information, so you can use the same software for testing environments.

# Virtual nodes

A virtual node represents a Corda identity, a person or business that wants to interact with other people or businesses using Corda. The virtual node also contains everything needed to communicate and transact on Corda: keys, certificates, and storage. This lets the holder of the identity join application networks, where they can interact with other group members according to the terms set by the Membership Group Manager (MGM). You can join multiple application networks from one physical node infrastructure using virtual nodes.

Virtual nodes can be:

* **Multi-tenant.** You can host multiple virtual nodes on one deployment of Corda, at no additional cost.
* **Portable.** You can move a virtual node from one host to another.
* **Highly available.** If you configure your node to be highly available, if it goes down, an identical one takes its place instantly.

## What is it made of?
Nothing—it's virtual! You can think of a virtual node as an environment that lets the processor locate a specific [Installer](#the-installer) file and the flows and contracts associated with that Installer. The Installer includes:

The information a virtual node needs to join a membership group.

CorDapp packages (CPKs), which are bundles of CorDapps—distributed applications that solve specific problems using Corda. The virtual node needs to install a specific set of CorDapps to join application networks.

The flows associated with the CPI let the virtual node communicate with others. The contracts define that virtual node's rules for verifying transactions.

## How does it work?
To get a node ready to interact with others on application networks, it must be onboarded. The virtual node creates a [sandbox](#sandbox)—an area where the [Installer](#the-installer) can exist in isolation, meaning it can't see any other tenants on the host deployment, and the other tenants can't see it. It associates that sandbox with a Corda identity and gets its keys, certificates, and storage.

Virtual nodes are built on several processes, which run independently and scale up and down based on need. These are called [workers](#workers), and can include the crypto worker, database worker, flow worker and persistence worker, depending on the topology of a specific deployment.

The processes communicate with each other using a message bus, Kafka. This makes sure the information ends up in the right place at the right time. The message bus:

Captures blobs of data, called events, that the processes generate.

Interprets the data and stores it until the moment it becomes relevant.

Delivers the message to the recipient process when the recipient process requires the information, preventing bottlenecks of information.

## How do virtual nodes communicate with each other?
Virtual nodes communicate using flows. The message bus that links the processes supporting the virtual node also communicates with the Corda gateway. The gateway sends flows between virtual nodes through a secure HTTPS link.

## How do I interact with my virtual node?
You can use remote procedure calls (RPC). This functionality is exposed by the HTTPS REST API.

You can use RPC to interact with the virtual node using:
* A command line interface (CLI).
* curl commands.

Virtual nodes generate a dynamic OpenAPI specification. You can locate this specification to get details of your virtual node's RPC functionality, including the endpoints, operations, operation parameters, and authentication methods.

# Sandboxes

Sandboxes are security mechanisms for separating running programs. They are the foundation that [virtual nodes](#virtual-nodes) run on, keeping contracts, workflows, and libraries separate from other code. The contents of these sandboxes are packaged up and sent to you to deploy using the [Installer](#the-installer) and participate in exchanges using Corda.

Each [virtual node](#virual-nodes) runs on top of one or more sandboxes. Unlike isolation, which hides code from users that shouldn't be able to see it, sandboxing prevents code from touching other parts of a system, where it could precipitate a failure or spread vulnerabilities.

Typically, a virtual node has sandboxes for its:

* Contract code. When you verify a contract on Corda, it checks that all versions of that contract comply with the code that existed in that sandbox when the contract was signed.

* Workflow.

* Third party libraries, if present.

Each of these sandboxes contains a distributable bundle of CorDapps, with a dependency tree and version information signed by the network publisher in a `.jar` file. This becomes a CorDapp package, which is part of a the software bundle distributed to participants through the [Installer](#the-installer).

# Workers

You can think of a worker as something you call and assign a task. The worker takes the task away to work on it, then calls you back when the task is complete. Some workers might pass parts of a task you give them to other specialized workers. You can call multiple workers to complete different tasks based on your needs at a given moment. Workers increase their capacity when they have a lot to do, and scale back when they don't. This property makes your Corda deployment resilient and scalable—you can add more workers if you need them, and add replicas of specific workers in case one fails.

What are they used for?
Workers power core elements of the Corda platform.
* The **flow worker** lets peers communicate and transact.
* The **database (DB) worker** handles the configuration of virtual nodes, CorDapps, the vault, membership group managers, HSM connections, and RPC.
* The **all-in-one worker** contains flow, crypto, database, and RPC processors. This is the lowest-cost configuration for small scale work.

## What are they made of?
Workers are Java Virtual Machine (JVM) processes that run in a cluster. This cluster of processes is what makes a virtual node work. Clusters are not always made from the same set of processes. That depends on the topology of a deployment—for example, if it consists of a single node or of multiple nodes. The cluster itself is made up of reusable components.

Most clusters operate in a cloud environment or in containers, which can run on different operating systems and infrastructures (see the support matrix for supported operating systems). Containers package up code and all its dependencies so the application runs quickly and reliably from one computing environment to another. Containers sit above the kernel and share a single instance of the kernel between all containers on the system. These environments are managed through an orchestration layer, such as Kubernetes or OpenShift. Operating clusters like this means that workers:

* Can be run in a hot/hot configuration. This enables multiple independent servers to receive traffic at all times. If one server becomes unavailable, it is quickly removed and clients retry. 
* Are self-healing. They can recover from from hardware failures, some bugs, and short outages of downstream systems. Recovery from longer term outages is also possible, if there are enough systems available to the cluster to provide acceptable service during the failure.
* Can easily be started from the orchestration layer using start-up parameters or environment variable in the event of a failover or scaling scenario.

## Components
A worker is an entry point to one or more processor bundles, a top-level component of a worker cluster. This is the "main" bundle, bootstrapped by the OSGi framework, that starts the JVM process. The worker encapsulates and directly references the processor. Processors are always independent from each other—all interaction between them goes through the message bus.

In addition to processors, you might find these components in a cluster:

* **HTTPS RPC service** — handles RPC requests from clients. It provides the HTTPS REST API for all interactions between the cluster or virtual nodes and external business systems. The RPC service is hot-hot load balanced. You can use the RPC service for all cluster functions, including setting up cluster configuration, editing RPC users, installing virtual nodes, installing CPIs, interacting with virtual nodes and flows, and monitoring.
* **Database service** — provides interaction with the long-term data store in the database. It translates requests for configuration change and virtual node operations into database changes, and routes vault queries from the flow engine to and from the appropriate virtual node schema.
* **Crypto server** — provides interaction with hardware security modules (HSMs) and software emulation of an HSM for all private key and certificate operations.
* **Flow engine service** — contains the flow state machine, which runs your flow code. Typically, the flow engine receives work for a particular flow and virtual node identity, accesses a suitable sandbox based on the [CPI] of the virtual node, and executes the next step of the flow. When the flow execution returns to the framework, it writes the checkpoint back to Kafka and sends events to the other worker processes to carry out the API functionality. The flow engine then takes the next task.
* **Gateway** — provides a secure HTTPS link between clusters that route messages between virtual nodes. You can run the gateway as a separate worker to enhance isolation.

## Libraries
Libraries are a collection of files, programs, routines, scripts, or functions that the components can reference. They are configured and managed externally. Libraries may be dependent on other libraries, but not on components.

## Who operates them?
The organization that deploys and configures a cluster is called the host operator. This may be a different organization than the one that creates the CPI software and manages virtual nodes. The host is the hosting machine for a cluster.