import sbt._
import Keys._

object PakkasBuild extends Build {

  lazy val pakkas = Project(
    id = "pakkas",
    base = file("."),
    settings = Defaults.defaultSettings ++ Seq(
      organization := "com.github.cb372",
      version := "0.1-SNAPSHOT",
      scalaVersion := "2.10.1",
      libraryDependencies ++= Seq(
          "com.typesafe.akka" %% "akka-actor" % "2.1.2"
      )
    )
  )
  
}

