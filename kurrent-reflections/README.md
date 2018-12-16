# kurrent-reflections

Simple implementations of EventRegistry and CommandRegistry that discover the available Events and Commands for a 
kurrent application by scanning the classpath. This saves manually registering them all, which is tedious and error 
prone. Events and Commands must be annotated with `@SerializedAs(name = ..)`,
where the name is the event/command identifier used by the event store/command handler.