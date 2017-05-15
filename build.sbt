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
  "org.tinylog" % "tinylog" % "1.2",
  "org.jsoup" % "jsoup" % "1.10.2"
)

/** Make sure to fork on run */
fork in run := true

/** Copy dependencies to file */
retrieveManaged := true