#!/usr/bin/env bash
set -e

clientRequestId="$RANDOM"
curl --insecure -u admin:admin -X POST \
   "https://localhost:8888/api/v1/flow/$ALICE_HASH" \
   -H 'accept: application/json' -H 'Content-Type: application/json' \
   -d '{"clientRequestId": "'$clientRequestId'",
  "flowClassName": "com.r3.corda.demo.interop.tokens.workflows.interop.SwapFlow",
   "requestBody": {
    "stateId" : "'"$STATE_ID"'",
    "newOwner" : "C=GB, L=London, O=Alice"
}}}'
printf "\n"
sleep 10
curl --insecure -u admin:admin -X GET "https://localhost:8888/api/v1/flow/$ALICE_HASH/$clientRequestId" -H 'accept: application/json'
printf "\n"