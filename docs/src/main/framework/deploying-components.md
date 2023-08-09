# Deploying Components

## ContainerCmd

`ContainerCmd` is a helper utility provided as a part of the framework. This helps component writers to start their components inside a container,
but can also be used to start standalone components (not in a container).

A main application needs to be created which uses the framework provided utility `csw.framework.deploy.containercmd.ContainerCmd` 
to start a container or standalone component. The utility supports the following parameters, which can be provided as arguments to the
application :

* fully qualified path of the configuration file
* **local** if the above path is a path to a file available on local disk. If this argument is not provided the file will be looked
up in the Configuration Service using the same path.

Scala
:   @@snip [ContainerCmdApp.scala](../../../../examples/src/main/scala/example/framework/ContainerCmdApp.scala) { #container-app }

Java
:   @@snip [JContainerCmdApp](../../../../examples/src/main/java/example/framework/JContainerCmdApp.java) { #container-app }

@@@ note

It is not necessary to have name of the application as ContainerCmdApp/JContainerCmdApp; the user can choose any name.

@@@

Starting a **standalone** component from a **local** configuration file

    `./container-cmd-app --local /assembly/config/standalone-assembly.conf`
    
Starting a **container** component from a configuration file available in **configuration service**

    `./container-cmd-app /assembly/config/container-assembly.conf`

## Container for deployment

A container is a component which starts one or more components and keeps track of the components within a single JVM process. When started, the container also registers itself with the Location Service.
The components to be hosted by the container are defined using a `ContainerInfo` model which has a set of ComponentInfo objects. It is usually described in a configuration file but can also be created programmatically.

SampleContainerInfo
:   @@@vars
    ```
    name = "Sample_Container"
    components: [
      {
        componentType = assembly
        componentHandlerClassName = package.component.SampleAssemblyHandlers
        prefix = wfos.SampleAssembly
        locationServiceUsage = RegisterAndTrackServices
        connections = [
          {
            prefix: wfos.Sample_Hcd_1
            componentType: hcd
            connectionType: pekko
          },
          {
            prefix: wfos.Sample_Hcd_2
            componentType: hcd
            connectionType: pekko
          },
          {
            prefix: wfos.Sample_Hcd_3
            componentType: hcd
            connectionType: pekko
          }
        ]
      },
      {
        prefix = "wfos.Sample_Hcd_1"
        componentType = hcd
        componentHandlerClassName = package.component.SampleHcdHandlers
        prefix = abc.sample.prefix
        locationServiceUsage = RegisterOnly
      },
      {
        prefix = "wfos.Sample_Hcd_2"
        componentType: hcd
        componentHandlerClassName: package.component.SampleHcdHandlers
        prefix: abc.sample.prefix
        locationServiceUsage = RegisterOnly
      }
    ]
    ```
    @@@
    
## Standalone components

A component can be run alone in a standalone mode without sharing its JVM space with any other component. 

Sample Info for an assembly
:   @@@vars
    ```
    componentType = assembly
    componentHandlerClassName = csw.common.components.command.ComponentHandlerForCommand
    prefix = tcs.mobie.blue.monitor.Monitor_Assembly
    locationServiceUsage = RegisterOnly
    ```
    @@@