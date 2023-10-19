{{/*
    State Manager Named Templates
    Most of the templates verify the value set for the [workerName.stateManager.db.host] property as that's the main
    indication of whether the user has configured the stateManager for the individual worker or not. When not configured,
    the templates use the cluster database details by default.
*/}}

{{/* State Manager Database Type */}}
{{- define "corda.stateManagerDbType" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.type -}}
{{-     else -}}
{{-         include "corda.clusterDbType" . -}}
{{-     end -}}
{{- end -}}

{{/* State Manager Database Host */}}
{{- define "corda.stateManagerDbHost" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.host -}}
{{-     else -}}
{{-         $.Values.db.cluster.host -}}
{{-     end -}}
{{- end -}}


{{/* State Manager Database Port */}}
{{- define "corda.stateManagerDbPort" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.port -}}
{{-     else }}
{{-         include "corda.clusterDbPort" $ -}}
{{-     end }}
{{- end -}}


{{/* State Manager Database Name */}}
{{- define "corda.stateManagerDbName" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{-     if $workerConfig.stateManager.db.host -}}
{{-         $workerConfig.stateManager.db.name -}}
{{-     else }}
{{-         include "corda.clusterDbName" $ -}}
{{-     end }}
{{- end -}}


{{/* State Manager JDBC URL */}}
{{- define "corda.stateManagerJdbcUrl" -}}
{{- $ := index . 0 }}
{{- $workerConfig := index . 1 }}
{{- if $workerConfig.stateManager.db.host -}}
jdbc:{{- include "corda.stateManagerDbType" (list $ $workerConfig) -}}://{{- $workerConfig.stateManager.db.host -}}:{{- include "corda.stateManagerDbPort" (list $ $workerConfig) -}}/{{- include "corda.stateManagerDbName" (list $ $workerConfig) -}}
{{- else -}}
jdbc:{{- include "corda.clusterDbType" $ -}}://{{- $.Values.db.cluster.host -}}:{{- include "corda.clusterDbPort" $ -}}/{{- include "corda.clusterDbName" $ -}}
{{- end -}}
{{- end -}}


{{/* State Manager Default Worker Secret Name */}}
{{- define "corda.stateManagerDefaultSecretName" -}}
{{- $ := index . 0 }}
{{- $workerName := index . 1 }}
{{ printf "%s-%s-state-manager-db" (include "corda.fullname" $) $workerName }}
{{- end -}}


{{/* State Manager Database Credentials Environment Variables */}}
{{- define "corda.stateManagerDbEnv" -}}
{{- $ := index . 0 }}
{{- $worker := index . 1 }}
- name: STATE_MANAGER_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if and ($worker.stateManager.db.host) ($worker.stateManager.db.username.valueFrom.secretKeyRef.name) }}
      name: {{ $worker.stateManager.db.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify $worker.stateManager.db.username.valueFrom.secretKeyRef.key" $worker.stateManager.db.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      {{- include "corda.clusterDbUsername" $ | nindent 6 }}
      {{- end }}
- name: STATE_MANAGER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if and ($worker.stateManager.db.host) ($worker.stateManager.db.password.valueFrom.secretKeyRef.name) }}
      name: {{ $worker.stateManager.db.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify $worker.stateManager.db.password.valueFrom.secretKeyRef.key" $worker.stateManager.db.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      {{- include "corda.clusterDbPassword" $ | nindent 6 }}
      {{- end }}
{{- end -}}


{{/* State Manager Database Credentials Environment Variables for Bootstrap Process */}}
{{- define "corda.bootstrapStateManagerDbEnv" -}}
{{- $ := index . 0 }}
{{- $worker := index . 1 }}
{{- $bootConfig := index . 2 }}
- name: STATE_MANAGER_PGUSER
  valueFrom:
    secretKeyRef:
      {{- if and ($worker.stateManager.db.host) ($bootConfig.username.valueFrom.secretKeyRef.name) }}
      name: {{ $bootConfig.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify $bootConfig.username.valueFrom.secretKeyRef.key" $bootConfig.username.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      {{- include "corda.bootstrapClusterPgUser" $ | nindent 6 }}
      {{- end }}
- name: STATE_MANAGER_PGPASSWORD
  valueFrom:
    secretKeyRef:
      {{- if and ($worker.stateManager.db.host) ($bootConfig.password.valueFrom.secretKeyRef.name) }}
      name: {{ $bootConfig.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required "Must specify $bootConfig.password.valueFrom.secretKeyRef.key" $bootConfig.password.valueFrom.secretKeyRef.key | quote }}
      {{- else }}
      {{- include "corda.bootstrapClusterPgPassword" $ | nindent 6 }}
      {{- end }}
{{- end -}}
