ems-redux
=========
[![Build Status](https://travis-ci.org/javaBin/ems-redux.svg)](https://travis-ci.org/javaBin/ems-redux)

API for exposing Events and their sessions with speakers.

EMS uses [Collection+JSON](http://www.amundsen.com/media-types/collection/) and further constrains the media-type.

## Additional Constraints
All collections in EMS are homogenious. This means there will only be one type of object in any given collection.

`Date` properties are formatted according to RFC-3339. Will always be set to Midnight. MUST be in UTC timezone.

`DateTime` properties are formatted according to RFC-3339.  MUST be in UTC timezone.

`URI` properties are normalized URIs according to RFC-3986.

Never assume that the properties listed are the only ones there. As the API evolves, new properties MAY appear. Clients
MUST be written to allow for this. Old clients MUST ignore unknown properties.

## Event
An event is a primary object type in EMS. An event consists of the following fields:

* `name` required, string.
* `venue` optional, string.
* `start` optional, `DateTime`.
* `end` optional, `DateTime`. If this appears, it MUST be a datetime after `start`.

## Session
A Session is always linked to a specific Event. A session consists of the following fields:

* `title` required, string.
* `summary` optional, string
* `body` optional, string
* `audience` optional, string
* `outline` optional, string
* `locale` required, two-letter iso code of language of the session. Usually 'en' or 'no'.
* `format` required, enumeration from the set ("presentation", "lightning-talk", "panel" or "bof"), if not filled, the server will assign a default.
* `level` required, enumeration from set set ("beginner", "beginner-intermediate", "intermediate",  "intermediate-advanced", "advanced"), if not filled, the server will assign a default.
* `state` optional, set by the server. May be set by the use of hypertext controls found elsewhere.
* `tags` optional, string array
* `keywords` optional, string array


## Contact
A Contact is a primary object type in EMS. A contact consists of the following fields:

* `name` required, string.
* `emails` required, array string. There MUST be at least 1 email.
* `bio` optional, string.
* `locale` optional, two-letter iso code of language of the contact. Usually 'en' or 'no'.

There will also be hypertext controls for the photo of the contact.

# Link relations
See the [wiki](https://github.com/javaBin/ems-redux/wiki/Link-Relations) for the link relations used.
All link relations not found in the registries defined in collection+json should be considered to have the base URI of
http://purl.org/javabin/rels/ems/

# Exploring the API
You can use Trygve Laugstøl's excellent [collection+json browser](http://collection-json-explorer.herokuapp.com/) to explore the API.

# Enabling authentication
The application is using JAAS for authentication.

The authentication is for now just Basic, meaning that the application should use HTTPS to make this more secure.

Set the system property 'java.security.auth.login.config' to the location of the config file.

If no system or security properties were set, try to read from the file,
${user.home}/.java.login.config, where ${user.home} is the value
represented by the "user.home" System property.

Example file:

    ems {
        com.sun.security.auth.module.LdapLoginModule REQUIRED
        userProvider="ldap://ldap.java.no"
        authIdentity="uid={USERNAME},ou=People,dc=java,dc=no"
        useSSL=false;
    };
