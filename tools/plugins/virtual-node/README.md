# Virtual Node CLI Plugin

Uploads and replaces a previously stored CPI record.
The plugin purges any sandboxes running an overwritten version of a CPI and optionally
vault data for the affected Virtual Nodes wiped out.

## commands
 - reset

### reset command

Example:
```bash
$ corda-cli vnode reset -t https://localhost:8888 -u admin -p password --cpi mycpifile.cpi -w
```

Flags:
 - `-t` `--target` The target address of the HTTP RPC server (e.g. `https://host:port`)
 - `-u` `--user` rpc username
 - `-p` `--password` rpc password
 - `-pv` `--protocol-version` NOT REQUIRED, defaults to 1
 - `-c` `--cpi` the cpi file to upload
 - `-w` `--wait` wait for the result, or have a result ID returned to be checked later.
