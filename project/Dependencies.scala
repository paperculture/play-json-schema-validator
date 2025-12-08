import sbt.*

object Version {
  final val playJson      = "2.9.4"
  final val playSpecs     = "2.9.9"
  final val scalaz        = "7.2.27"  // 7.3.x - breaks type inference in validation code
  final val specs2        = "4.23.0"  // 5.x - requires scala 3
  final val guava         = "33.5.0-jre"
  final val i18n          = "1.1.0"
  final val galimatias    = "0.2.1"  // Latest available (project unmaintained)
  final val narshornCore  = "15.7"
}

object Library {
  final val guava         = "com.google.guava"   % "guava"                   % Version.guava
  final val scalaz        = "org.scalaz"         %% "scalaz-core"            % Version.scalaz
  final val playJson      = "com.typesafe.play"  %% "play-json"              % Version.playJson
  final val playTest      = "com.typesafe.play"  %% "play-specs2"            % Version.playSpecs      % "test"
  final val playServer    = "com.typesafe.play"  %% "play-akka-http-server"  % Version.playSpecs      % "test"
  final val specs2        = "org.specs2"         %% "specs2-core"            % Version.specs2         % "test"
  final val i18n          = "com.osinka.i18n"    %% "scala-i18n"             % Version.i18n
  final val galimatias    = "io.mola.galimatias" % "galimatias"              % Version.galimatias
  final val nashorn       = "org.openjdk.nashorn" % "nashorn-core"           % Version.narshornCore
}


object Dependencies {
  import Library.*

  val core = List(
    galimatias,
    guava,
    i18n,
    playJson,
    playTest,
    playServer,
    scalaz,
    specs2,
    nashorn
  )
}