// Comment to get more information during initialization
logLevel := Level.Warn

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.0.12")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.3.13")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")

addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
