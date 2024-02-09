{{/*
    Database Related Templates / Helpers
*/}}


{{/*
    Default Name for Secrets Containing Bootstrap Database Credentials
    The resulting secret name is "chartName-bootstrap-databaseId-db"
*/}}
{{- define "corda.db.bootstrapCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $dbId := index . 1 -}}
{{ printf "%s-bootstrap-%s-db" ( include "corda.fullname" $ ) $dbId }}
{{- end -}}


{{/*
    Environment variables (*_DB_USERNAME and *_DB_PASSWORD) to be used when configuring databases
*/}}
{{- define "corda.db.runtimeEnvironment" -}}
{{- $ := index . 0 -}}
{{- $db := index . 1 -}}
{{- $bootstrapSettings := index . 2 -}}
- name: {{ upper ( snakecase $db ) }}_DB_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if (($bootstrapSettings.username.valueFrom).secretKeyRef).name }}
      name: {{ $bootstrapSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify bootstrap.db.%s.username.valueFrom.secretKeyRef.key" $db ) $bootstrapSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ printf "%s-%s-db" ( include "corda.fullname" $ ) ( kebabcase $db ) }}
      key: "username"
      {{-   end }}
- name: {{ upper ( snakecase $db ) }}_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if (($bootstrapSettings.password.valueFrom).secretKeyRef).name }}
      name: {{ $bootstrapSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify bootstrap.db.%s.password.valueFrom.secretKeyRef.key" $db ) $bootstrapSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ printf "%s-%s-db" ( include "corda.fullname" $ ) ( kebabcase $db ) }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/*
    Environment variables (CONFIG_*_DB_USERNAME and CONFIG_*_DB_PASSWORD) to be used when configuring config database
    for each worker
*/}}
{{- define "corda.db.runtimeConfigEnvironment" -}}
{{- $ := index . 0 -}}
{{- range $workerName, $workerValues := $.Values.workers }}
{{-   if $workerValues.config }}
{{-     $configValues := $workerValues.config }}
- name: CONFIG_{{ upper ( snakecase $workerName ) }}_DB_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if (($configValues.username.valueFrom).secretKeyRef).name }}
      name: {{ $configValues.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify workers.%s.config.username.valueFrom.secretKeyRef.key" $workerName ) $configValues.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.workerCredentialsSecretName" ( list $ $workerName ) }}
      key: "username"
      {{-   end }}
- name: CONFIG_{{ upper ( snakecase $workerName ) }}_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if (($configValues.password.valueFrom).secretKeyRef).name }}
      name: {{ $configValues.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify workers.%s.config.password.valueFrom.secretKeyRef.key" $workerName ) $configValues.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.workerCredentialsSecretName" ( list $ $workerName ) }}
      key: "password"
      {{-   end }}
{{-   end -}}
{{- end -}}
{{- end -}}


{{/*
    Environment variables (BOOTSTRAP_*_DB_USERNAME and BOOTSTRAP_*_DB_PASSWORD) to be used when bootstrapping databases
*/}}
{{- define "corda.db.bootstrapEnvironment" -}}
{{- $ := index . 0 -}}
{{- $db := index . 1 -}}
{{- $dbId := index . 2 -}}
{{- $bootstrapSettings := index . 3 -}}
- name: BOOTSTRAP_{{ upper ( snakecase $db ) }}_DB_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if (($bootstrapSettings.username.valueFrom).secretKeyRef).name }}
      name: {{ $bootstrapSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify bootstrap username.valueFrom.secretKeyRef.key for database '%s'" $dbId ) $bootstrapSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.bootstrapCredentialsSecretName" ( list $ $dbId ) | quote }}
      key: "username"
      {{-   end }}
- name: BOOTSTRAP_{{ upper ( snakecase $db ) }}_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if (($bootstrapSettings.password.valueFrom).secretKeyRef).name }}
      name: {{ $bootstrapSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify bootstrap password.valueFrom.secretKeyRef.key for database '%s'" $dbId ) $bootstrapSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.bootstrapCredentialsSecretName" ( list $ $dbId ) | quote }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/*
    Environment variables (CONFIG_DB_USERNAME and CONFIG_DB_PASSWORD) to be used when connecting to config database
*/}}
{{- define "corda.db.workerConfigEnvironment" -}}
{{- $ := index . 0 -}}
{{- $workerName := index . 1 -}}
{{- $config := index . 2 -}}
{{- $dbId := $config.storageId -}}
- name: CONFIG_DB_USERNAME
  valueFrom:
    secretKeyRef:
      {{- if (($config.username.valueFrom).secretKeyRef).name }}
      name: {{ $config.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify username.valueFrom.secretKeyRef.key for database '%s'" $dbId ) $config.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.workerCredentialsSecretName" ( list $ $workerName ) | quote }}
      key: "username"
      {{-   end }}
- name: CONFIG_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      {{- if (($config.password.valueFrom).secretKeyRef).name }}
      name: {{ $config.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $dbId ) $config.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.workerCredentialsSecretName" ( list $ $workerName ) | quote }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/*
    Default Name for Secrets Containing Database Credentials for a worker
    The resulting secret name is "chartName-worker-workerName-db"
*/}}
{{- define "corda.db.workerCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $workerName := index . 1 -}}
{{ printf "%s-%s-worker-db" ( include "corda.fullname" $ ) ( kebabcase $workerName ) }}
{{- end -}}


{{/*
    Generate Java Database Connectivity URL From a 'database' Structure
*/}}
{{- define "corda.db.connectionUrl" -}}
jdbc:{{- .type -}}://{{- required ( printf "Must specify a host for database '%s'" .id ) .host -}}:{{- .port -}}/{{- .name -}}
{{- end -}}


{{/*
    Generate Java Database Connectivity Driver Class Name From a 'database' Type (already validated in JSON schema)
*/}}
{{- define "corda.db.driverClassName" -}}
{{- if eq .type "postgresql" -}}
{{-   printf "org.postgresql.Driver" -}}
{{- end -}}
{{- end -}}


{{/*
    Iterate through configured platform databases and return the one matching the requested 'storageId'.
    If a database with the requested 'storageId' can not be found, immediately fail the rendering process.
*/}}
{{- define "corda.db.configuration" -}}
{{- $ := index . 0 -}}
{{- $dbId := index . 1 -}}
{{- $reference := index . 2 -}}
{{- $databaseFound := false -}}
{{- $defaultDatabaseConfig := dict -}}
{{- range $.Values.databases -}}
{{-   if eq .id $dbId -}}
{{-     $databaseFound = true -}}
{{-     $defaultDatabaseConfig = . -}}
{{-   end -}}
{{- end -}}
{{- if not $databaseFound -}}
{{-   fail ( printf "Persistent storage '%s' referenced at %s undefined in databases" $dbId $reference ) -}}
{{- end -}}
{{ $defaultDatabaseConfig | toYaml }}
{{- end -}}


{{/*
    Iterate through configured bootstrap databases and return the one matching the requested 'storageId'.
    If a database with the requested 'storageId' can not be found, immediately fail the rendering process.
*/}}
{{- define "corda.db.bootstrapConfiguration" -}}
{{- $ := index . 0 -}}
{{- $dbId := index . 1 -}}
{{- $reference := index . 2 -}}
{{- $databaseFound := false -}}
{{- $defaultDatabaseConfig := dict -}}
{{- range $.Values.bootstrap.db.databases -}}
{{-   if eq .id $dbId -}}
{{-     $databaseFound = true -}}
{{-     $defaultDatabaseConfig = . -}}
{{-   end -}}
{{- end -}}
{{- if not $databaseFound -}}
{{-   fail ( printf "Persistent storage '%s' referenced at %s undefined in bootstrap.db.databases" $dbId $reference ) -}}
{{- end -}}
{{ $defaultDatabaseConfig | toYaml }}
{{- end -}}
