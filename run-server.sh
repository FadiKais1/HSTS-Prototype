#!/usr/bin/env bash
mvn -q compile exec:java -Dexec.mainClass=hsts.server.MainServer
