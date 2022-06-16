val root = project
  .in(
    file(".")
  )
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    scalaVersion := "3.1.1",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-hikari" % "1.0.0-RC2",
      "org.tpolecat" %% "doobie-postgres" % "1.0.0-RC2",
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value,
      "org.postgresql" % "postgresql" % "42.4.0"
    )
  )
