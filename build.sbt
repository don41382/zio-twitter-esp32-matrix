name := "zio-example"

version := "0.1"

scalaVersion := "2.12.8"

resolvers ++= Seq(
  "Typesafe Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
  "Secured Central Repository" at "https://repo1.maven.org/maven2",
  Resolver.sonatypeRepo("snapshots")
)


val ZIOVersion = "1.0.0-RC11-1"
val GIFVersion = "4.9.0"
val twitterVersion = "6.1"
val logbackVersion = "1.1.9"

libraryDependencies ++= Seq(
  // ZIO
  "dev.zio" %% "zio" % ZIOVersion,
  // twitter
  "com.danielasfregola" %% "twitter4s" %  twitterVersion,
  // logging
  "ch.qos.logback"      % "logback-classic" % logbackVersion
)

mainClass in (Compile, run) := Some("com.rocketsolutions.TwitterDisplay")
