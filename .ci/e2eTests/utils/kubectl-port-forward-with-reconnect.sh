#!/usr/bin/env bash

if [ "$#" -ne 4 ]; then
    echo "Illegal Parameter Count!!"
    echo "Usage: ${0##*/} Namespace ResourceName LocalPort TargetPort"
    exit 1
fi

while true; do
	kubectl --namespace "${1}" port-forward "${2}" "${3}:${4}" 2>&1
	echo "kubectl port-forward connection to ${2}:${4} lost, sleeping 1 seconds before trying again..."
	sleep 1
done
