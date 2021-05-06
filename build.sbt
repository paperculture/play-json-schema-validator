val Repositories = Seq(
  "Typesafe Repository"           at "https://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots"        at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype OSS Releases"         at "https://oss.sonatype.org/content/repositories/releases"
)

val commonSettings = Seq(
  organization := "com.eclipsesource",
  scalaVersion := "2.12.0",
  crossScalaVersions := Seq("2.12.8", "2.13.0"),
  licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  Keys.fork in Test := false,
  Keys.parallelExecution in Test := false
)

val releaseSettings = Seq(
  githubOwner := "paperculture",
  githubRepository := "play-json-schema-validator",
  githubTokenSource := TokenSource.GitConfig("github.token"),
  publishArtifact in Test := false,
  publishMavenStyle := true,
  pomIncludeRepository := { _ => false },
  pomExtra :=
    <url>https://github.com/eclipsesource/play-json-schema-validator</url>
      <scm>
    <connection>scm:git:github.com/eclipsesource/play-json-schema-validator.git</connection>
    <developerConnection>scm:git:git@github.com:eclipsesource/play-json-schema-validator.git</developerConnection>
    <url>github.com/eclipsesource/play-json-schema-validator</url>
    </scm>
    <developers>
    <developer>
    <id>eclipsesource</id>
    <name>EclipseSource</name>
    <url>http://www.eclipsesource.com/</url>
      </developer>
    </developers>,
  publishTo := githubPublishTo.value,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
)

val buildSettings = Defaults.coreDefaultSettings ++ commonSettings

val testSettings = unmanagedJars in Test ++= Seq(
  baseDirectory.value / "src/test/resources/simple-schema.jar",
  baseDirectory.value / "src/test/resources/simple-schema-issue-65.jar",
  baseDirectory.value / "src/test/resources/issue-65.jar"
)

lazy val schemaProject = Project("play-json-schema-validator", file("."))
  .settings(buildSettings)
  .settings(releaseSettings)
  .settings(testSettings)
  .settings(
    resolvers ++= Repositories,
    retrieveManaged := true,
    libraryDependencies ++= Dependencies.core,
    testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")
  )
