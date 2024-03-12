ThisBuild / version := "1.0.0-SNAPSHOT"

val scala213Version = "2.13.13"
ThisBuild / scalaVersion := scala213Version
ThisBuild / crossScalaVersions := Seq(scala213Version, "2.12.19", "3.3.3")

ThisBuild / resolvers += Resolver.ApacheMavenSnapshotsRepo

val PekkoVersion = "1.0.2"
val PekkoConnectorsVersion = "1.0.2"
val GoogleAuthVersion = "1.23.0"
val protobufJavaVersion = "3.25.3"

lazy val root = (project in file("."))
  .settings(
    name := "pekko-connectors-google-cloud-pub-sub-grpc",
    licenses := List(License.Apache2),
    libraryDependencies ++= Seq(
      "com.google.cloud" % "google-cloud-pubsub" % "1.127.1" % "protobuf-src",
      "io.grpc" % "grpc-auth" % org.apache.pekko.grpc.gen.BuildInfo.grpcVersion,
      "com.google.auth" % "google-auth-library-oauth2-http" % GoogleAuthVersion,
      "com.google.protobuf" % "protobuf-java" % protobufJavaVersion % Runtime,
      "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
      "org.apache.pekko" %% "pekko-discovery" % PekkoVersion,
      "org.apache.pekko" %% "pekko-connectors-google-common" % PekkoConnectorsVersion,
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.10.0" % Test,
      "org.apache.pekko" %% "pekko-stream-testkit" % PekkoVersion % Test,
      "org.apache.pekko" %% "pekko-slf4j" % PekkoVersion % Test,
      "ch.qos.logback" % "logback-classic" % "1.3.14" % Test,
      "org.scalatest" %% "scalatest" % "3.2.18" % Test,
      "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.41.0" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test,
      "junit" % "junit" % "4.13.2" % Test
    ),
    pekkoGrpcCodeGeneratorSettings ~= { _.filterNot(_ == "flat_package") },
    pekkoGrpcGeneratedSources := Seq(PekkoGrpc.Client),
    pekkoGrpcGeneratedLanguages := Seq(PekkoGrpc.Scala, PekkoGrpc.Java),
    // for the ExampleApp in the tests
    run / connectInput := true,
    Compile / scalacOptions ++= Seq(
      "-Wconf:src=.+/pekko-grpc/main/.+:s",
      "-Wconf:src=.+/pekko-grpc/test/.+:s"),
    compile / javacOptions := (compile / javacOptions).value.filterNot(_ == "-Xlint:deprecation")).enablePlugins(
    PekkoGrpcPlugin
  )
