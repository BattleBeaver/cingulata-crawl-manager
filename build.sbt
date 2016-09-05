enablePlugins(JavaAppPackaging)

name := "Cingulata Crawl Manager"
organization := "org.cingulata"
version := "1.0"
scalaVersion := "2.11.8"

lazy val jsoupVersion = "1.8.3"
lazy val casbahVersion = "3.0.0"
lazy val slf4jVersion = "1.7.12"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV       = "2.4.3"
  val scalaTestV  = "2.2.6"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaV,
    "org.scalatest"     %% "scalatest" % scalaTestV % "test"
  )
}

//MongoDB
libraryDependencies += "org.mongodb" %% "casbah" % casbahVersion
//JSoup
libraryDependencies += "org.jsoup" % "jsoup" % jsoupVersion

//SLF4J, LOG4J
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.13"
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.4.1"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.4.1"


Revolver.settings
