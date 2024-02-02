{{/*
    State Manager Named Templates
    TODO-[CORE-19372]: rename this file to _stateManager.tpl (and remove the old one _stateManager.tpl).
*/}}


{{/*
    Checks whether there's at least one state type configured under the specified 'workerConfig'
*/}}
{{- define "corda.sm.required" -}}
{{- $ := index . 0 -}}
{{- $workerConfig := index . 1 -}}
{{- $stateManagerV2 := false -}}
{{- range $stateType, $stateTypeConfig  := $.Values.stateManager }}
{{-   $stateManagerSettings := ( index $workerConfig "stateManager" ) -}}
{{-   if and $stateManagerSettings ( index $stateManagerSettings $stateType ) -}}
{{-     $stateManagerV2 = true -}}
{{-   end -}}
{{- end }}
{{- $stateManagerV2 -}}
{{- end -}}


{{/*
    Name for Secrets Containing State Manager Runtime Credentials (defined at the worker level)
    The resulting secret name is "chartName-runtime-workerNameKebabCase-stateTypeKebabCase-db"
*/}}
{{- define "corda.sm.runtimeCredentialsSecretName" -}}
{{- $ := index . 0 -}}
{{- $stateType := index . 1 -}}
{{- $workerName := index . 2 -}}
{{ printf "%s-runtime-%s-%s-db" ( include "corda.fullname" $ ) ( include "corda.kebabCase" $workerName ) ( include "corda.kebabCase" $stateType ) }}
{{- end -}}


{{/*
    Name for Volumes Containing State Manager Runtime Credentials (defined at the worker level)
*/}}
{{- define "corda.sm.runtimeCredentialsVolumeName" -}}
{{ printf "%s-volume" ( include "corda.kebabCase" . ) }}
{{- end -}}

{{/*
    Environment variables (BOOT_PG_USERNAME and BOOT_PG_PASSWORD) to be used when bootstrapping state manager databases
*/}}
{{- define "corda.sm.db.bootstrapEnvironment" -}}
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
      name: {{ include "corda.db.bootstrapCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "username"
      {{-   end }}
- name: BOOT_PG_PASSWORD
  valueFrom:
    secretKeyRef:
      {{-   if $bootstrapSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $bootstrapSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $bootstrapSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else }}
      name: {{ include "corda.db.bootstrapCredentialsSecretName" ( list $ $dbName ) | quote }}
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
{{- define "corda.sm.db.runtimeEnvironment" -}}
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
      name: {{ include "corda.sm.runtimeCredentialsSecretName" ( list $ $stateType $workerName ) | quote }}
      key: "username"
      {{-   else if $defaultSettings.username.valueFrom.secretKeyRef.name }}
      name: {{ $defaultSettings.username.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify username.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $defaultSettings.username.valueFrom.secretKeyRef.key | quote }}
      {{-   else  }}
      name: {{ include "corda.db.runtimeCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "username"
      {{-   end }}
- name: STATE_MANAGER_PASSWORD
  valueFrom:
    secretKeyRef:
      {{-   if $runtimeSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $runtimeSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify workers.%s.stateManager.%s.password.valueFrom.secretKeyRef.key" $workerName $stateType ) $runtimeSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else if $runtimeSettings.password.value }}
      name: {{ include "corda.sm.runtimeCredentialsSecretName" ( list $ $stateType $workerName ) | quote }}
      key: "password"
      {{-   else if $defaultSettings.password.valueFrom.secretKeyRef.name }}
      name: {{ $defaultSettings.password.valueFrom.secretKeyRef.name | quote }}
      key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $dbName ) $defaultSettings.password.valueFrom.secretKeyRef.key | quote }}
      {{-   else  }}
      name: {{ include "corda.db.runtimeCredentialsSecretName" ( list $ $dbName ) | quote }}
      key: "password"
      {{-   end }}
{{- end -}}


{{/*
    State Manager Containers to Create & Apply Database Schemas Within The Bootstrap Job
*/}}
{{- define "corda.sm.db.bootstrapContainers" -}}
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
            {{- include "corda.sm.db.bootstrapEnvironment" ( list $ $dbName $bootstrapSettings ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev
              echo "Generating Database Specification for Database '{{ $dbName }}'..."
              JDBC_URL={{ include "corda.db.connectionUrl" $databaseConfig | quote }}
              mkdir /tmp/database-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }}
              java -Dpf4j.pluginsDir=/opt/override/plugins -Dlog4j2.debug=false -jar /opt/override/cli.jar database spec \
                -s "statemanager" -g "statemanager:{{ $schemaName }}" \
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
            {{- include "corda.sm.db.bootstrapEnvironment" ( list $ $dbName $bootstrapSettings ) | nindent 12 }}
            {{- include "corda.sm.db.runtimeEnvironment" ( list $ $dbName $stateType $workerName $databaseConfig $runtimeSettings ) | nindent 12 }}
          command: [ 'sh', '-c', '-e' ]
          args:
            - |
              #!/bin/sh
              set -ev
              echo "Applying State Manager Specification for Database '{{ $dbName }}'..."
              export PGPASSWORD="${BOOT_PG_PASSWORD}"
              find /tmp/database-{{ $workerKebabCase }}-{{ $stateTypeKebabCase }} -iname "*.sql" | xargs printf -- ' -f %s' | xargs psql -v ON_ERROR_STOP=1 -h "{{- required ( printf "Must specify a host for database '%s'" $dbName ) $databaseConfig.host -}}" -p "{{- $databaseConfig.port -}}" -U "${BOOT_PG_USERNAME}" --dbname "{{- $dbName -}}"
              echo 'Applying State Manager Specification for {{ $workerName }}... Done!'

              echo 'Creating users and granting permissions for State Manager in {{ $workerName }}...'
              psql -v ON_ERROR_STOP=1 -h "{{- required ( printf "Must specify a host for database '%s'" $dbName ) $databaseConfig.host -}}" -p "{{- $databaseConfig.port -}}" -U "${BOOT_PG_USERNAME}" "{{- $dbName -}}" << SQL
                DO \$\$ BEGIN IF EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${STATE_MANAGER_USERNAME}') THEN RAISE NOTICE 'Role "${STATE_MANAGER_USERNAME}" already exists'; ELSE CREATE USER "${STATE_MANAGER_USERNAME}" WITH ENCRYPTED PASSWORD '${STATE_MANAGER_PASSWORD}'; END IF; END \$\$;
                GRANT USAGE ON SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
                GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
                GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA "{{- $schemaName -}}" TO "${STATE_MANAGER_USERNAME}";
                ALTER ROLE "${STATE_MANAGER_USERNAME}" SET search_path TO "{{- $schemaName -}}";
              SQL

              echo "Applying State Manager Specification for Database '{{ $dbName }}'... Done!"
          volumeMounts:
            - mountPath: /tmp
              name: temp
{{- end -}}
{{- end -}}


{{/*
    Volumes Containing State Manager Runtime Credentials for Each State Type Used by The Worker
    The template defines a single 'volume' per State Type, with two projections pointing to the Secrets where the
    username and password are configured.
*/}}
{{- define "corda.sm.runtimeCredentialVolumes" -}}
{{- $ := index . 0 -}}
{{- $workerName := index . 1 -}}
{{- $workerConfig := index . 2 -}}
{{- range $stateType, $runtimeSettings := $workerConfig.stateManager }}
{{-   $volumeName := include "corda.sm.runtimeCredentialsVolumeName" $stateType }}
{{-   $stateTypeRootConfig := ( index $.Values.stateManager $stateType ) }}
{{-   $connectionSettings := fromYaml ( include "corda.db.configuration" ( list $ $stateTypeRootConfig.storageId ( printf "stateManager.%s.storageId" $stateType ) ) ) }}
- name: {{ $volumeName }}
  projected:
    sources:
      - secret:
{{-   if $runtimeSettings.username.valueFrom.secretKeyRef.name }}
          name: {{ $runtimeSettings.username.valueFrom.secretKeyRef.name | quote }}
          items:
            - key: {{ required ( printf "Must specify workers.%s.stateManager.%s.username.valueFrom.secretKeyRef.key" $workerName $stateType ) $runtimeSettings.username.valueFrom.secretKeyRef.key | quote }}
              path: "username"
{{-   else if $runtimeSettings.username.value }}
          name: {{ include "corda.sm.runtimeCredentialsSecretName" ( list $ $stateType $workerName ) | quote }}
          items:
            - key: "username"
              path: "username"
{{-   else if $connectionSettings.username.valueFrom.secretKeyRef.name }}
          name: {{ $connectionSettings.username.valueFrom.secretKeyRef.name | quote }}
          items:
            - key: {{ required ( printf "Must specify username.valueFrom.secretKeyRef.key for database '%s'" $connectionSettings.name ) $connectionSettings.username.valueFrom.secretKeyRef.key | quote }}
              path: "username"
{{-   else  }}
          name: {{ include "corda.db.runtimeCredentialsSecretName" ( list $ $connectionSettings.name ) | quote }}
          items:
            - key: "username"
              path: "username"
{{-   end }}
      - secret:
{{-   if $runtimeSettings.password.valueFrom.secretKeyRef.name }}
          name: {{ $runtimeSettings.password.valueFrom.secretKeyRef.name | quote }}
          items:
            - key: {{ required ( printf "Must specify workers.%s.stateManager.%s.password.valueFrom.secretKeyRef.key" $workerName $stateType ) $runtimeSettings.password.valueFrom.secretKeyRef.key | quote }}
              path: "password"
{{-   else if $runtimeSettings.password.value }}
          name: {{ include "corda.sm.runtimeCredentialsSecretName" ( list $ $stateType $workerName ) | quote }}
          items:
            - key: "password"
              path: "password"
{{-   else if $connectionSettings.password.valueFrom.secretKeyRef.name }}
          name: {{ $connectionSettings.password.valueFrom.secretKeyRef.name | quote }}
          items:
            - key: {{ required ( printf "Must specify password.valueFrom.secretKeyRef.key for database '%s'" $connectionSettings.name ) $connectionSettings.password.valueFrom.secretKeyRef.key | quote }}
              path: "password"
{{-   else  }}
          name: {{ include "corda.db.runtimeCredentialsSecretName" ( list $ $connectionSettings.name ) | quote }}
          items:
            - key: "password"
              path: "password"
{{-   end }}
{{- end }}
{{- end -}}


{{/*
    Volume Mounts for Each State Type Used by The Worker
*/}}
{{- define "corda.sm.runtimeCredentialsVolumeMounts" -}}
{{- $ := index . 0 -}}
{{- $workerConfig := index . 1 -}}
{{- range $stateType, $runtimeSettings := $workerConfig.stateManager }}
{{-   $volumeName := include "corda.sm.runtimeCredentialsVolumeName" $stateType }}
- mountPath: "/tmp/{{- $stateType -}}-mount"
  name: {{ $volumeName | quote }}
  readOnly: true
{{- end }}
{{- end -}}


{{/*
    State Manager Container to Create Worker Startup Configuration File
    There is one configuration file generated for each State Type used by the Worker:
        - Connection settings are obtained from the database configuration.
        - Connection Pool settings are obtained from the worker runtime configuration.
        - Credentials are obtained worker runtime configuration and, if not found, from the database configuration.
*/}}
{{- define "corda.sm.db.runtimeConfigurationContainer" -}}
{{- $ := index . 0 -}}
{{- $workerName := index . 1 -}}
{{- $workerConfig := index . 2 -}}
{{- $workerKebabCase := include "corda.kebabCase" $workerName -}}
{{- with index . 0 }}
- name: {{ ( printf "generate-%s-runtime-configuration" $workerKebabCase ) | quote }}
  image: {{ include "corda.workerImage" ( list $ $workerConfig ) }}
  imagePullPolicy: {{ $.Values.imagePullPolicy }}
  {{- include "corda.containerSecurityContext" $ | nindent 2 }}
  command: [ 'sh', '-c', '-e' ]
  args:
    - |
      #!/bin/sh
      set -ev
      echo 'Generating State Manger Configuration Settings...'

      {{ range $stateType, $stateTypeRuntimeConfig  := $workerConfig.stateManager }}
      {{-   $stateTypeRootConfig := ( index $.Values.stateManager $stateType ) -}}
      {{-   $connectionSettings := fromYaml ( include "corda.db.configuration" ( list $ $stateTypeRootConfig.storageId ( printf "stateManager.%s.storageId" $stateType ) ) ) -}}
      cat << EOF >> "/work/{{- $stateType -}}-config.json"
      {
        "stateManager": {
          "{{- $stateType -}}": {
            {{/* TODO-[CORE-19372]: align case with boot schema in corda-api */ -}}
            "type": "{{- $stateTypeRuntimeConfig.type | upper -}}",
            "database": {
              "jdbc": {
                "url": {{ include "corda.db.connectionUrl" $connectionSettings | quote }},
                "driver": {{ include "corda.db.driverClassName" $connectionSettings | quote }}
              },
              "pool": {
              {{- if $stateTypeRuntimeConfig.connectionPool.minSize }}
                "minSize": {{ $stateTypeRuntimeConfig.connectionPool.minSize }},
              {{- end }}
                "maxSize": {{ $stateTypeRuntimeConfig.connectionPool.maxSize -}},
                "idleTimeoutSeconds": {{ $stateTypeRuntimeConfig.connectionPool.idleTimeoutSeconds -}},
                "maxLifetimeSeconds": {{ $stateTypeRuntimeConfig.connectionPool.maxLifetimeSeconds -}},
                "keepAliveTimeSeconds": {{ $stateTypeRuntimeConfig.connectionPool.keepAliveTimeSeconds -}},
                "validationTimeoutSeconds": {{ $stateTypeRuntimeConfig.connectionPool.validationTimeoutSeconds }}
              },
              "user": "$(cat /tmp/{{ $stateType }}-mount/username)",
              "pass": "$(cat /tmp/{{ $stateType }}-mount/password)"
            }
          }
        }
      }
      EOF
      {{ end }}
      echo 'Generating State Manger Configuration Settings... Done!'
  volumeMounts:
    - mountPath: "/work"
      name: "work"
      readOnly: false
  {{- include "corda.sm.runtimeCredentialsVolumeMounts" ( list $ $workerConfig )  | nindent 4 -}}
{{- end -}}
{{- end -}}


{{/*
    State Manager Startup Configuration File Parameters
*/}}
{{- define "corda.sm.runtimeConfigurationParameters" -}}
{{-  range $stateType, $stateTypeRuntimeConfig  := .stateManager }}
- "--values=/work/{{- $stateType -}}-config.json"
{{- end }}
{{- end -}}
