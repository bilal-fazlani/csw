# Publishing State

A component has access to a `currentStatePublisher` Actor, which can be used to publish its @ref:[CurrentState](../params/states.md). Any subscriber of this component will receive the 
published state. 

@@@ note

This feature is provided for optimized communication between an Assembly and an HCD only.  See @ref:[Matching state for command completion](../commons/command.md#matching-state-for-command-completion).

@@@

`CurrentState` can be used in a number of ways. Two use cases are:

1. An HCD can have detailed information that it publishes for its Assemblies periodically. The Assemblies use the CurrentState
information to create events that describe the state of the Assembly and HCD.
2. 'CurrentState' information published by the HCD can be used by an Assembly to complete commands that have been submitted
to the Assembly.

The CommandService shows examples of the use of `CurrentState` for the second use case. See @ref:[Matching state for command completion](../commons/command.md#matching-state-for-command-completion).

A subscriber can subscribe to all `CurrentState` published by an HCD or to specific `CurrentState` specified by its 
`StateName`. The publisher does not need to do anything special to support these features.

Scala
:   @@snip [SampleComponentHandlers.scala](../../../../examples/src/main/scala/org/tmt/csw/sample/CurrentStateExampleComponentHandlers.scala) { #currentStatePublisher }

Java
:   @@snip [JSampleComponentHandlers.java](../../../../examples/src/main/java/org/tmt/csw/sample/JCurrentStateExampleComponentHandlers.java) { #currentStatePublisher }
