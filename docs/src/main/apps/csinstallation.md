# Coursier Installation

In TMT, we have adopted coursier to set up scala/java environment on development/production machines and install java based
applications provided by CSW and ESW.

## Install and Setup, Coursier and Jdk 17

1. Refer to the steps mentioned @link:[here](https://get-coursier.io/docs/cli-installation) { open=new } for downloading and installing coursier launcher

1. Run following command to set up coursier

```bash
./cs setup
cs java --jvm temurin:1.17
```

The above command does the following things:

   1. It adds coursier bin directory where coursier installs applications to PATH environment variable. So that, all the applications installed via coursier are available in PATH and can be executed from any directory.
   1. It installs Java Eclipse Temurin JDK 17.


Once jvm installed - include the output of command `cs java --jvm temurin:1.17 --env` in your respective shell profiles
- In Fish: add `eval (cs java --jvm temurin:1.17 --env)` into fish.config
- In Zsh: add `eval $(cs java --jvm temurin:1.17 --env)` into .zshrc

Above config will set the required environment variable(e.g., JAVA_HOME) into your shell.

@@@ note

Refer @link:[this](https://get-coursier.io/docs/cli-setup) { open=new } for detailed guide on `cs setup` command

@@@

## Add TMT Channel

In order to install applications provided by CSW and ESW, TMT channel needs to be added to local coursier installation using `cs install` command.

For developer machine setup, add `apps.json` channel,

```bash
cs channel --add https://raw.githubusercontent.com/tmtsoftware/osw-apps/master/apps.json
```

For production machine setup, add `apps.prod.json` channel,

```bash
cs channel --add https://raw.githubusercontent.com/tmtsoftware/osw-apps/master/apps.prod.json
```

@@@ note

TMT dev channel `apps.json` is pre-configured with `INTERFACE_NAME` and `TMT_LOG_HOME` environment variable.
So that developer does not need to manually configure these environment variables on dev machine.

@@@

## Examples

The command `cs install <app-name>` installs an application `<app-name>` using coursier.  

### Installing Alarm CLI (with the version present in apps.json)

```bash
// csw-alarm-cli is the name of the application installed
cs install csw-alarm-cli
```

*Note:* In case you get a Connection Refused error, try setting the @ref:[Environment Variables](../deployment/env-vars.md) (INTERFACE_NAME etc.).

### Installing Alarm CLI Latest Version(using apps.prod.json channel)

If version number or SHA not provided in the `install` command, then the latest tagged binary of application gets installed.

```bash
// installs Alarm CLI with latest tagged version
cs install csw-alarm-cli
```

*Note:* Set the @ref:[Environment Variables](../deployment/env-vars.md) before running the app through `apps.prod.json`
