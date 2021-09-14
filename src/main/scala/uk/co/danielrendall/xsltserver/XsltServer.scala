package uk.co.danielrendall.xsltserver

@main def xsltServer() =
  val port = Option(System.getProperty("port")).map(_.toInt).getOrElse(8080)
  println("PORT =" + port)

