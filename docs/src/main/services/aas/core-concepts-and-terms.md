# Core concepts and terms

These are some common terms used in AAS. More information is available in the
Keycloak [documentation](https://www.keycloak.org/docs/latest/server_admin/index.html#core-concepts-and-terms)

### Users

Users are entities that are able to log into your system. 
They can have attributes associated with themselves like email, username, address, phone number, and birthday. 
They can be assigned group membership and have specific roles assigned to them.

### Roles

Roles identify a type or category of user. Admin, user, manager, and employee are all typical roles that 
may exist in an organization. Applications often assign access to specific roles rather 
than individual users as dealing with users can be too fine grained and hard to manage.

### Permissions

Permissions are another way of specifying access in the form of a Scope and a Resource.
For example, "scope: Sell; resource: Vehicle" combined
specifies that the user with this scope and resource combination can "sell vehicles".

### Realms

A realm manages a set of users, credentials, roles, and groups. A user belongs to and logs into a realm. 
Realms are isolated from one another and can only manage and authenticate the users that they control.

### Clients

Clients are entities that can request AAS to authenticate a user. Most often, clients are applications 
and services that want to use AAS to secure themselves and provide a single sign-on solution. 
Clients can also be entities that just want to request identity information or an access token so that they 
can securely invoke other services on the network that are secured by AAS.

### Client Roles

Clients can define roles that are specific to them. This is basically a role namespace dedicated to the client.



