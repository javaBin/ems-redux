ems-redux
=========

A complete rewite of EMS using Collection+JSON

The project is split into two applications

Server
======

Contains the API which all other applications will use to talk to the database.
The Server is linked to a MongoDB database.


Cake
=====

Administration application.
This application is where you add new Events, Create/Update sessions and speakers.

Jetty
=====
In order to be able to run both applications this project is just a booter for Jetty
spawing off webapps to make us able to run both applications in the same VM and
avoiding CORS problems.
