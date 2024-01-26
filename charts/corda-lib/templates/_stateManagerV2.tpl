{{/*
    State Manager Named Templates
    Kept in an isolated file for easier maintenance and development.
    TODO-[CORE-19372]: rename this file to _stateManager.tpl (and remove the old one _stateManager.tpl).
*/}}


{{/*
    Transform the given string input into kebab case
*/}}
{{- define "corda.kebabCase" -}}
{{ . | kebabcase | replace "p-2p" "p2p" }}
{{- end }}


{{/*
    Default Name for Secrets Containing Bootstrap Database Credentials
    The resulting secret name is "chartName-bootstrap-databaseName-db"
*/}}
{{- define "corda.defaultDatabaseBootstrapCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{ printf "%s-bootstrap-%s-db" ( include "corda.fullname" $ ) $dbName }}
{{- end -}}


{{/*
    Default Name for Secrets Containing Database Credentials
    The resulting secret name is "chartName-runtime-databaseName-db"
*/}}
{{- define "corda.defaultDatabaseRuntimeCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{ printf "%s-runtime-%s-db" ( include "corda.fullname" $ ) $dbName }}
{{- end -}}


{{/*
    Name for Secrets Containing State Manager Runtime Credentials (custom, defined at the worker level)
    The resulting secret name is "chartName-runtime-workerNameKebabCase-stateTypeKebabCase-db"
*/}}
{{- define "corda.stateManagerDefaultRuntimeSecretName" -}}
{{- $ := index . 0 -}}
{{- $stateType := index . 1 -}}
{{- $workerName := index . 2 -}}
{{ printf "%s-runtime-%s-%s-db" ( include "corda.fullname" $ ) ( include "corda.kebabCase" $workerName ) ( include "corda.kebabCase" $stateType ) }}
{{- end -}}


{{/*
    Environment variables to be used when bootstrapping state manager databases (BOOT_PG_USERNAME and BOOT_PG_PASSWORD)
*/}}
{{- define "corda.stateManagerDatabaseBootstrapEnvironment" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{- $bootstrapSettings := index . 2 -}}
- name: BOOT_PG_USERNAME
  valueFrom:
    secretKeyRef:
      {{-   if $bootstrapSettings.username.valueFrom.secretKeyRef.name }}
      name: {{ $bootstrapSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify username.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $bootstrapSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.defaultDatabaseBootstrapCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "username"
      {{-   end }}
- name: BOOT_PG_PASSWORD
  valueFrom:
    secretKeyRef:
      {{-   if $bootstrapSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $bootstrapSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $bootstrapSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.defaultDatabaseBootstrapCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/*
    Environment variables to be used when using state manager databases (STATE_MANAGER_USERNAME and STATE_MANAGER_PASSWORD)
    The order of preference when choosing the actual values (username and password) are shown below:
        - Custom, set at the worker level through a kubernetes secret provided by the user.
        - Custom, set at the worker level through a plain value provided by the user (transformed into a Secret by the chart)
        - Default, set at the root "databases" level through a kubernetes secret provided by the user.
        - Default, set at the root "databases" level through a plain value provided by the user (transformed into a Secret by the chart)
*/}}
{{- define "corda.stateManagerDatabaseRuntimeEnvironment" -}}
{{- $ := index . 0 -}}
{{- $dbName := index . 1 -}}
{{- $stateType := index . 2 -}}
{{- $workerName := index . 3 -}}
{{- $defaultSettings := index . 4 -}}
{{- $runtimeSettings := index . 5 -}}
- name: STATE_MANAGER_USERNAME
  valueFrom:
    secretKeyRef:
      {{-   if $runtimeSettings.username.valueFrom.secretKeyRef.name }}
      name: {{ $runtimeSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify workers.%s.stateManager.%s.username.valueFrom.secretKeyRef.key" $workerName $stateType ) $runtimeSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else if $runtimeSettings.username.value }}
      name: {{ include "corda.stateManagerDefaultRuntimeSecretName" ( list $ $stateType $workerName ) | quote }}
      key: "username"
      {{-   else if $defaultSettings.username.valueFrom.secretKeyRef.name }}
      name: {{ $defaultSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify username.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $defaultSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else  }}
      name: {{ include "corda.defaultDatabaseRuntimeCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "username"
      {{-   end }}
- name: STATE_MANAGER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{-   if $runtimeSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $runtimeSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify workers.%s.stateManager.%s.password.valueFrom.secretKeyRef.key" $workerName $stateType ) $runtimeSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else if $runtimeSettings.password.value }}
      name: {{ include "corda.stateManagerDefaultRuntimeSecretName" ( list $ $stateType $workerName ) | quote }}
      key: "password"
      {{-   else if $defaultSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $defaultSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $defaultSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else  }}
      name: {{ include "corda.defaultDatabaseRuntimeCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/* State Manager Containers to Create & Apply Database Schemas Within The Bootstrap Job */}}
{{- define "corda.stateManagerDatabaseBootstrap" -}}
{{- $ := index . 0 -}}
{{- $stateType := index . 1 -}}
{{- $workerName := index . 2 -}}
{{- $schemaName := index . 3 -}}
{{- $databaseConfig := index . 4 -}}
{{- $runtimeSettings := index . 5 -}}
{{- $bootstrapSettings := index . 6 -}}
{{- with index . 0 -}}
{{- $dbName := $databaseConfig.name -}}
{{- $workerKebabCase := include "corda.kebabCase" $workerName -}}
{{- $stateTypeKebabCase := include "corda.kebabCase" $stateType -}}
{{/*    -- We use two init-containers for serial execution to prevent issues when applying the same Liquibase files at the same time (developer use case where multiple workers use the same state manager database) */}}
        - name: generate-db-schema-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }}
          image: {{ include "corda.bootstrapCliImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
            {{- include "corda.bootstrapCliEnv" . | nindent 12 }}
            {{- include "corda.stateManagerDatabaseBootstrapEnvironment" ( list $ $dbName $bootstrapSettings ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev
              echo "Generating Database Specification for Database '{{ $dbName }}'..."
              JDBC_URL="jdbc:{{- $databaseConfig.type -}}://{{- required ( printf "Must specify a host for database '%s'" $dbName ) $databaseConfig.host -}}:{{- $databaseConfig.port -}}/{{- $dbName -}}"
              mkdir /tmp/database-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "{{ $schemaName }}" -g "{{ $schemaName }}:state_manager" \
                -u "${BOOT_PG_USERNAME}" -p "${BOOT_PG_PASSWORD}" \
                --jdbc-url "${JDBC_URL}" \
                -c -l /tmp/database-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }}
              echo "Generating Database Specification for Database '{{ $dbName }}'... Done"
          workingDir: /tmp
          volumeMounts:
            - mountPath: /tmp
              name: temp
            {{- include "corda.log4jVolumeMount" . | nindent 12 }}
        - name: apply-db-schema-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }}
          image: {{ include "corda.bootstrapDbClientImage" . }}
          imagePullPolicy: {{ .Values.imagePullPolicy }}
          {{- include "corda.bootstrapResources" . | nindent 10 }}
          {{- include "corda.containerSecurityContext" . | nindent 10 }}
          env:
            {{- include "corda.stateManagerDatabaseBootstrapEnvironment" ( list $ $dbName $bootstrapSettings ) | nindent 12 }}
            {{- include "corda.stateManagerDatabaseRuntimeEnvironment" ( list $ $dbName $stateType $workerName $databaseConfig $runtimeSettings ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev
              echo 'Applying State Manager Specification for Database '{{ $dbName }}'..."
              export PGPASSWORD="${BOOT_PG_PASSWORD}"
              find /tmp/database-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }} -iname "*.sql" | xargs printf -- ' -f %s' | xargs psql -v ON_ERROR_STOP=1 -h "{{- required ( printf "Must specify a host for database '%s'" $dbName ) $databaseConfig.host -}}" -p "{{- $databaseConfig.port -}}" -U "${BOOT_PG_USERNAME}" --dbname "{{- $dbName -}}"
              echo 'Applying State Manager Specification for {{ $workerName }}... Done!'

              echo 'Creating users and granting permissions for State Manager in {{ $workerName }}...'
              psql -v ON_ERROR_STOP=1 -h "{{- required ( printf "Must specify a host for database '%s'" $dbName ) $databaseConfig.host -}}" -p "{{- $databaseConfig.port -}}" -U "${BOOT_PG_USERNAME}" "{{- $dbName -}}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${STATE_MANAGER_USERNAME}') THEN RAISE NOTICE 'Role "${STATE_MANAGER_USERNAME}" already exists'; ELSE CREATE USER "${STATE_MANAGER_USERNAME}" WITH ENCRYPTED PASSWORD '${STATE_MANAGER_PASSWORD}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
              SQL

              echo 'Applying State Manager Specification for Database '{{ $dbName }}'... Done!'
          volumeMounts:
            - mountPath: /tmp
              name: temp
{{- end -}}
{{- end -}}
