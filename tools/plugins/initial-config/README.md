# Initial Configuration Plug-in

This is a plug-in for the `corda-cli`
that creates the SQL files for installing the initial configuration we need to get the HTTP gateway 
for the API up and running.

This has several subcommand to create different bits of configuration

- `create-user-config` creates the content for the RBAC tables for an initial admin user.
- `create-db-config` creates the content for the DB connections table
- `create-crypto-config` creates the content for the initial crypto worker configuration
