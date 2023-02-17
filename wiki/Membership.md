# Membership Overview
The membership functionality in the `corda-runtime-os` encapsulates the network management of Corda 5 and is built on top of the peer-to-peer layer. It is the successor to CENM from Corda 4.

# Network types
There are two available types of networks; a static network, and a dynamic network.

## Static networks
Static networks are intended for test purposes where the list of virtual nodes or members in the network are predetermined. This type of network is limited only to a single cluster due to the fact that there is no MGM running to distribute member data across clusters. 

In order to run a static network, the following high level steps must be completed:
* Start up a corda cluster
* Define the members in the group in the `GroupPolicy.json` file
* Package the `GroupPolicy.json` file into a CPI.
* Upload the CPI to your cluster.
* Create a virtual node in your cluster for each member defined in the group policy file.
* Register each member in the group.

The following wiki pages cover the above steps in more detail:
* [Local development with Kubernetes](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes)
* [Group Policy](https://github.com/corda/corda-runtime-os/wiki/Group-Policy)
* [Static network member registration](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Static-Networks))

## Dynamic networks
Dynamic networks are for production networks, or for testing across multiple clusters or when the number of members are not predetermined in your test network. One of the main difference here to static networks is that there is a running MGM available which distributes the member list and which all members must register with before being able to transact among the group.

In order to run a dynamic network, the following high level steps:
* Start up a corda cluster
* Create an MGM `GroupPolicy.json` file.
* Package MGM `GroupPolicy.json` file into an MGM CPI.
* Upload the CPI to your cluster.
* Create a virtual node in your cluster for the MGM.
* Assign required HSMs for the MGM.
* Create required keys, and optionally import required certificates.
* Use the register endpoint to finalise the MGM setup so that it is ready to accept members.
* Export the `GroupPolicy.json` file that members require to join the group.
* Package this `GroupPolicy.json` file into a member CPI.
* Upload this CPI to the cluster.
* Create the virtual node for the member.
* Assign required HSMs for the MGM.
* Create required keys, and optionally import required certificates.
* Use the register endpoint to request membership from the MGM.

The following wiki pages cover the above steps in more detail:
* [Local development with Kubernetes](https://github.com/corda/corda-runtime-os/wiki/Local-development-with-Kubernetes)
* [Group Policy](https://github.com/corda/corda-runtime-os/wiki/Group-Policy)
* [MGM onboarding](https://github.com/corda/corda-runtime-os/wiki/MGM-Onboarding)
* [Dynamic network member registration](https://github.com/corda/corda-runtime-os/wiki/Member-Onboarding-(Dynamic-Networks))

# Components
## Overview
The membership code contains components intended only for use internally, and components designed for use in areas of the platform which require membership data or functionality. This section covers the module pairs which expose membership data or functionality for use by other non-membership components in corda-runtime-os. Module pairs are comprised of an interface module and an implementation module. These modules are:
* [Group policy provider](#GroupPolicyProvider) — provides look ups of group policy files for holding identities after they have been parsed from the CPI.
* [Membership client](#MemberOpsClient) — client component for calling membership functionality across workers (such as starting registration from the RPC worker).
* [Membership group reader](#MembershipGroupReaderProvider) — client component for maintaining a local cache of group data (e.g. member lists) and performing lookups.

Specific components in these modules are described below.

## `GroupPolicyProvider`
### Description
The group policy provider supplies a holding identity's `GroupPolicy` object. It is delivered as a `GroupPolicy.json` file in a `.cpi`. CPI installation should include parsing this json as a string and publishing it to the message bus for this component to pick up.

Refer to the [Group Policy](https://github.com/corda/corda-runtime-os/wiki/Group-Policy) wiki page for more information of this file.

### Implementation
The `GroupPolicyProvider` module has only one implementation, which depends on the **virtual node read component**, and the **`.cpi` information read component**. 
1. The virtual node read component retrieves virtual node information for the holding identity.
2. The `.cpi` information component retrieves the node's `.cpi` metadata, which includes the group policy files as a string. 
3. This `GroupPolicyProvider` component implementation parses the string into a `GroupPolicy` object.

`GroupPolicy` objects are cached, so multiple reads return the same object. The cache clears when the component stops or if it goes down due to down dependencies.

### Usage
The `GroupPolicyProvider` is used to expose the `GroupPolicy` objects internally to any interested services. This component can be included with any worker requiring group policy lookups (e.g. the member or p2p worker). 
For static networks, `GroupPolicy.json` defines the static member list. This component is used in the static registration implementation of the `MemberRegistrationService` to parse static group configurations. Also during registration, the group policy lookup is used to decide which registration implementation to use for a member.


## `MembershipGroupReaderProvider`
### Description
This component provides a group reader for a holding identity. A network member can call it to access the group data it has permission to see, such as group parameters, and to access functionality such as member lookups.

### Implementation
There is only one implementation of this component, which creates group reader instances on request and caches them for faster lookups later. It also creates subscriptions to receive group data, which it caches and uses later to create the group readers as needed. These caches are cleared when this component stops or goes down and they are recreated when the component starts or comes back up.

### Usage
Any internal component can use the `MembershipGroupReaderProvider` if it requires member lookups or a member's view of group data, such as group parameters or the `.cpi` allow list.
For example, P2P components can use it to look up member information.

## `MemberOpsClient`
### Description
This client component allows member services to be grouped in one component and potentially be called remotely from another processor. The member components can also run in the same process and the client component could still call member services. This provides flexibility in splitting where services are run.
`MemberOpsClient` uses DTOs in its API so as to break any dependency between the HTTP API and the client so that can evolve independently. As a result, the RPC processor handles starting this component in the case of the HTTP endpoints rather than the HTTP API endpoint component.

### Implementation
There is a single implementation of this client.

### Usage
As an example, the registration services are currently deployed as part of a member processor, while the membership endpoints are deployed as part of the RPC worker. In this example, this client component is also deployed on the RPC processor and calls are forwarded over the message bus to the member worker for processing.

---
# Services
## `MembershipGroupReader`
### Description
The `MembershipGroupReader` service is closely connected to the `MembershipGroupReaderProvider` component. This class should only be initialized by the `MembershipGroupReaderProvider` and should always be accessed via that component i.e. references to instances of `MembershipGroupReader` should not be held long term. Instead use the provider component to get the group reader each time you are trying to read group data.

`MembershipGroupReader` is created in the context of a holding ID. It is used to retrieve a specific members view on the group. It will primarily be used for member lookups, but can also provide access to the group parameters and CPI allow list. 

Instances of `MembershipGroupReader` do not implement `Lifecycle`. Instead, the `MembershipGroupReaderProvider` described previously has lifecycle and the reader services provide member views on the member group cache. The `MembershipGroupReaderProvider` can modify/clear cached data in response to lifecycle events and updated data, which is why references to the `MembershipGroupReader` instances should not be held.
