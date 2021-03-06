lazy val commonSettings = Seq(
  organization := "org.fedoraproject",
  version := "0.1",
  scalaVersion := "2.11.8"
)

lazy val root = (project in file(".")).
  aggregate(core, frontend).
  settings(commonSettings: _*).
  settings(
    name := "java-deptools"
  )

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  enablePlugins(JavaAppPackaging)

lazy val frontend = (project in file("frontend")).
  settings(commonSettings: _*).
  enablePlugins(PlayScala).
  dependsOn(core)
