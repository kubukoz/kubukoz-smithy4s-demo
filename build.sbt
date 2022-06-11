val root = project
  .in(
    file(".")
  )
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    scalaVersion := "2.13.8",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value,
      "software.amazon.smithy" % "smithy-protocol-test-traits" % "1.21.0" % Smithy4s
    ),
    smithy4sAllowedNamespaces := List("smithy.test", "example")
  )
