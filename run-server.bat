@echo off
mvn -q compile exec:java -Dexec.mainClass=hsts.server.MainServer
pause
