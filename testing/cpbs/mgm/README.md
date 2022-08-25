# MGM CPB

This is MGM CPB is intentionally empty and is for use when onboarding an MGM since an MGM doesn't have any specific flows it can run at this point.

This empty CPB can be combined with an MGM `GroupPolicy.json` file such as the one below to create an MGM CPI to upload to a cluster.
``` json
{
  "fileFormatVersion": 1,
  "groupId": "CREATE_ID",
  "registrationProtocol": "net.corda.membership.impl.registration.dynamic.mgm.MGMRegistrationService",
  "synchronisationProtocol": "net.corda.membership.impl.synchronisation.MgmSynchronisationServiceImpl"
}
```