/** Name of project */
name := "Scache"

/** Project Version */
version := "1.0"

/** Scala version */
scalaVersion := "2.12.2"

/** Scalac parameters */
scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

/** Javac parameters */
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

/** Resolver */
resolvers += "Search Maven" at "https://repo1.maven.org/maven2/"

/** Source Dependencies */
libraryDependencies ++= Seq(
  "org.apache.commons" % "commons-lang3" % "3.4",
  "com.typesafe.akka" % "akka-stream_2.12" % "2.5.1",
  "com.typesafe.akka" % "akka-http_2.12" % "10.0.6",
  "com.lihaoyi" % "upickle_2.12" % "0.4.4",
  "org.tinylog" % "tinylog" % "1.2",
  "org.json" % "json" % "20170516",
  "ch.megard" % "akka-http-cors_2.12" % "0.2.1"
)

/** Make sure to fork on run */
fork in run := true

/** Copy dependencies to file */
retrieveManaged := true