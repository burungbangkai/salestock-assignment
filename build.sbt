name := """sale-stock-assignment"""

organization := """com.github.burungbangkai"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

routesGenerator := InjectedRoutesGenerator

resolvers ++= Seq(
    "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
    "Local Maven Repository" at "file:///"+Path.userHome.absolutePath+"/.m2/repository",
    "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
)
libraryDependencies ++= Seq(
    "com.github.jeroenr" %% "tepkin" % "0.7",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0" % "test"
)

fork in run := true
