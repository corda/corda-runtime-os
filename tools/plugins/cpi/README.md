# CPI CLI Plugin

This plugin upload the CPI using Corda CLI

## commands
- cpi

### Upload sub-command
- upload

Example:
```bash
$ corda-cli cpi upload -t https://localhost:8888 -u admin -p password --cpi mycpifile.cpi -w -k
```

Flags:
- `-t` `--target` The target address of the REST server (e.g. `https://host:port`)
- `-u` `--user` REST username
- `-p` `--password` REST password
- `-c` `--cpi` the cpi file to upload
- `-w` `--wait` wait for the result, or have a result ID returned to be checked later.
- `-k` `--insecure` Allow for invalid Server-side SSL certificates

### List cpi's sub-command
- list

Example:
```bash
$ corda-cli cpi list -t https://localhost:8888 -u admin -p password -k
```

Flags:
- `-t` `--target` The target address of the REST server (e.g. `https://host:port`)
- `-u` `--user` REST username
- `-p` `--password` REST password
- `-k` `--insecure` Allow for invalid Server-side SSL certificates