val PekkoVersion = "1.7.1"
val PekkoHttpVersion = "1.4.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "prediction-texte",
    version := "0.2.0",
    scalaVersion := "3.3.9",
    scalacOptions ++= Seq("-feature", "-deprecation", "-Wunused:all"),
    Compile / mainClass := Some("main"),
    run / fork := true,
    // SQLite charge une bibliothèque native : une JVM dédiée évite les conflits de classloader.
    Test / fork := true,
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
      "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion,
      "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion,
      "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion,
      "org.xerial" % "sqlite-jdbc" % "3.54.1.0",
      "org.slf4j" % "slf4j-api" % "2.0.20",
      "ch.qos.logback" % "logback-classic" % "1.5.38",
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.apache.pekko" %% "pekko-http-testkit" % PekkoHttpVersion % Test,
      "org.apache.pekko" %% "pekko-stream-testkit" % PekkoVersion % Test,
      "org.apache.pekko" %% "pekko-testkit" % PekkoVersion % Test
    ),
    assembly / mainClass := Some("main"),
    assembly / assemblyJarName := "predictive-text-app.jar",
    assembly / assemblyMergeStrategy := {
      // Les services JDBC et Logback doivent survivre à la fusion.
      case PathList("META-INF", "services", _*) =>
        MergeStrategy.filterDistinctLines
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "reference.conf"         => MergeStrategy.concat
      case "module-info.class"      => MergeStrategy.discard
      case _                        => MergeStrategy.first
    }
  )
