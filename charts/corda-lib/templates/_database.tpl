{{/*
    Database Related Templates / Helpers
*/}}


{{/*
    Default Name for Secrets Containing Bootstrap Database Credentials
    The resulting secret name is "chartName-bootstrap-databaseName-db"
*/}}
{{- define "corda.db.bootstrapCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{ printf "%s-bootstrap-%s-db" ( include "corda.fullname" $ ) $dbName }}
{{- end -}}


{{/*
    Default Name for Secrets Containing Database Credentials
    The resulting secret name is "chartName-runtime-databaseName-db"
*/}}
{{- define "corda.db.runtimeCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{ printf "%s-runtime-%s-db" ( include "corda.fullname" $ ) $dbName }}
{{- end -}}


{{/*
    Generate Java Database Connectivity URL From a 'database' Structure
*/}}
{{- define "corda.db.connectionUrl" -}}
jdbc:{{- .type -}}://{{- required ( printf "Must specify a host for database '%s'" .name ) .host -}}:{{- .port -}}/{{- .name -}}
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
{{- $dbName := index . 1 -}}
{{- $reference := index . 2 -}}
{{- $databaseFound := false -}}
{{- $defaultDatabaseConfig := dict -}}
{{- range $.Values.databases -}}
{{-   if eq .name $dbName -}}
{{-     $databaseFound = true -}}
{{-     $defaultDatabaseConfig = . -}}
{{-   end -}}
{{- end -}}
{{- if not $databaseFound -}}
{{-   fail ( printf "Undefined persistent storage '%s' detected at %s" $dbName $reference ) -}}
{{- end -}}
{{ $defaultDatabaseConfig | toYaml }}
{{- end -}}
