#!/bin/bash

SCRIPT_DIR=$(dirname ${BASH_SOURCE[0]})
set -e
source "$SCRIPT_DIR"/settings.sh

kubectl delete ns $(kubectl get ns | grep ^$NAMESPACE_PREFIX | awk '{print $1}') --wait || echo ''

killall kubectl || echo ''
