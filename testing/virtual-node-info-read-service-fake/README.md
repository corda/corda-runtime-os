# Virtual Node Info Service Fake

An in-memory fake implementation of the `VirtualNodeInfoReadService`.

The service can be pre-populated with data from a file. The file should be named `virtual-node-info-read-service.yaml`
and placed in the working directory, e.g.:

```yaml
# virtual-node-info-read-service-fake.yaml

virtualNodeInfos:
  - holdingIdentity:
      x500Name: 'CN=Alice, O=Alice Corp, L=LDN, C=GB'
      groupId: flow-worker-dev
    cpiIdentifier:
      name: flow-worker-dev
      version: 5.0.0.0-SNAPSHOT
    cryptoDmlConnectionId: 04e8e967-e174-45e7-aea1-8da6daa107e1
    vaultDmlConnectionId: 929a21c5-687c-469d-9347-0eb7e99e722a
```