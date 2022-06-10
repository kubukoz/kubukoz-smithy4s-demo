val root = project
  .in(
    file(".")
  )
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    scalaVersion := "2.13.8",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-aws-http4s" % smithy4sVersion.value
    )
  )
