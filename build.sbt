val scala3Version = "3.0.2"

lazy val root = project
  .in(file("."))
  .settings(
    name := "xslt-server",
    version := "0.1.0",

    scalaVersion := scala3Version,

    assembly / mainClass := Some("uk.co.danielrendall.xsltserver.xsltServer"),

    libraryDependencies ++= Seq(
      "org.nanohttpd" % "nanohttpd" % "2.3.1",
      "net.sf.saxon" % "Saxon-HE" % "10.5",
      "com.novocode" % "junit-interface" % "0.11" % "test"
    )
  )
