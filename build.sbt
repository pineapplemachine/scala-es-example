name := "scala-es-example"
scalaVersion := "2.11.8"
resolvers += "gphat" at "https://raw.github.com/gphat/mvn-repo/master/releases/" // wabisabi
resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
libraryDependencies ++= Seq(
    "wabisabi" %% "wabisabi" % "2.1.1",
    "com.ning" % "async-http-client" % "1.8.17",
    "com.typesafe.play" % "play-json_2.11" % "2.3.4",
    "com.github.nscala-time" %% "nscala-time" % "2.16.0"
)
