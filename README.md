----
# Welcome to Elastic Core! #

<a href="https://scan.coverity.com/projects/ordinarydude-elastic-core">
  <img alt="Coverity Scan Build Status"
       src="https://scan.coverity.com/projects/10946/badge.svg"/>
</a> <img src="https://travis-ci.org/OrdinaryDude/elastic-core.svg?branch=master"></img> [![contributions welcome](https://img.shields.io/badge/contributions-welcome-brightgreen.svg?style=flat)](https://github.com/OrdinaryDude/elastic-core/issues)

## Installing and Running Elastic (Testnet) ##

### Preliminaries ###

First of all, you need Git installed.

Then, if you go the preferred way, all you need is Docker.

Otherwise, if you want to build everything from scratch, make sure you have Java Development Kit 1.8 (http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) installed, the JRE will not suffice. If you have only JRE installed, you will get an error, that the `javac` command can't be found. Furthermore, you will need to have Maven installed. Also ... get Linux or macOS ;-) Of course it works on Windows too, the process might however look slightly different.


### Using Docker (preferred) ###

Get the Docker repository:

`git clone https://github.com/OrdinaryDude/elastic-docker.git`

Now, use the simple scripts to create the docker container and launch an instance.
So, first of all: create the container (this has to be done only once):

`./build_docker.sh`

Now you can use

`./run_docker.sh`

to launch Elastic, and

`./stop_docker.sh`

to stop it again.

The web wallet will listen on http://localhost:6876.

If you want to remove Elastic entirely, just do:

`./deinstall_docker.sh`

### Alternative: Compiling from Scratch ###

Go to your "Development" folder and issue:

`git clone https://github.com/OrdinaryDude/elastic-pl.git`

Go into the elastic-pl/ directory, and issue

`mvn compile package install`

to install the Elastic Programming Language in your local Maven repository. This will be required as a dependency for Elastic. Now, go back to the original path (`cd ..`).

Now, do

`git clone https://github.com/OrdinaryDude/elastic-core.git`

in order to checkout the fresh source code, followed by going into the elastic-core directory and issuing

`mvn compile package`

to compile and package the code. Afterwards, you can launch the elastic client by running

`java -jar target/elastic-core*.jar`

### Getting Some XEL for Testing ###

You can obtain some testnet XEL from the faucet located at http://elasticexplorer.org/faucet.

### Updating Source Code to the Latest Version ###

If you already have an old elastic version running and you now want to update XEL to the newest version, first stop your node (CTRL + C).
Go to main directory of XEL and issue the following commands.

`git pull;
mvn clean compile package;
rm -rf elastic_test_db/`

This will delete all old blockchain data, and recompile the updated source code. If you get any error message that you have unstashed changes, stash them using

`git stash`

first and (if you need to) unstash them after compiling using

`git stash apply`

Then, you can run elastic again using

`java -jar target\elastic-core-*.jar
`
