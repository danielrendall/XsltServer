package uk.co.danielrendall.xsltserver

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.{IHTTPSession, MIME_PLAINTEXT, MIME_TYPES, Method, Response, newFixedLengthResponse}

import java.io.{ByteArrayOutputStream, InputStream}
import java.util
import javax.xml.transform.{Templates, TransformerFactory}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Using}
import scala.jdk.CollectionConverters.MapHasAsScala

/**
 * The server itself
 */
class XsltServerApp(port: Int) extends NanoHTTPD(port):

  object Constants:
    val QUIT = "_quit"
    // cURL sents an "Expect" header if the size is too big; rather than figure out how to deal with that,
    // we impose a limit which should be safe. See https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Expect
    val MAX_BODY_SIZE = 8388603 // 8MB

  println("Server using port " + port)

  private val tf: TransformerFactory = TransformerFactory.newInstance()

  /**
   * Map of user-visible name of XSLT to the MD5 hash of that XSLT; so if we have the same XSLT bound to a number of
   * different names, we don't waste our time compiling them.
   */
  private val nameToHash: mutable.Map[String, String] =
    new mutable.HashMap[String, String]()

  /**
   * Map of MD5 hash of an XSLT to the compiled Templates for that XSLT
   */
  private val hashToTemplates: mutable.Map[String, Templates] =
    new mutable.HashMap[String, Templates]()

  /**
   * Map of user-visible name of XSLT to and default parameters which should always be passed to that XSLT (which can
   * be added to via query-string params)
   */
  private val nameToDefaultParams: mutable.Map[String, Map[String, String]] =
    new mutable.HashMap[String, Map[String, String]]()

  override def serve(session: IHTTPSession): Response =
    // URI always starts with "/"
    session.getUri.tail.split("/").filterNot(_.isEmpty).toList match {
      case head::tail =>
        if (head == Constants.QUIT) {
          quit()
        } else {
          session.getMethod match {
             case Method.GET => get(session, head, tail)
             case Method.POST => post(session, head, tail)
             case Method.PUT => put(session, head, tail)
             case Method.DELETE => delete(session, head, tail)
             case _ => badRequest("Unsupported method: " + session.getMethod.name())
           }
        }
      case _ =>
        runningMessage()
    }

  private def get(session: IHTTPSession, first: String, rest: List[String]) =
    okMsg("GET " + first)

  private def post(session: IHTTPSession, first: String, rest: List[String]) =
    okMsg("POST " + first)

  private def put(session: IHTTPSession, first: String, rest: List[String]) =
    if (first.startsWith("_")) {
      badRequest("Identifiers starting with underscores are reserved")
    } else {
      getUploadedBytes(session) match {
        case Some(byteArrayTry) =>
          byteArrayTry match {
            case Success(byteArray) =>
              okMsg("PUT " + first + " " + byteArray.size + " bytes")
            case Failure(ex) =>
              badRequest(ex.getMessage)
          }
        case None =>
          okMsg("PUT " + first + " no bytes")
      }
    }

  private def delete(session: IHTTPSession, first: String, rest: List[String]) =
    okMsg("DELETE " + first)

  private def getUploadedBytes(session: IHTTPSession): Option[Try[Array[Byte]]] =
    val bodySize: Int = getBodySize(session)
    if (bodySize == 0)
      None
    else if (bodySize >  Constants.MAX_BODY_SIZE)
      Option(Failure(RequestTooBigException(bodySize)))
    else
      Option {
        Try {
          val baos = new ByteArrayOutputStream(bodySize)
          StreamUtils.copy(session.getInputStream(), baos, bodySize)
          baos.toByteArray
        }
      }

  private def getBodySize(session: IHTTPSession): Int =
    session.getHeaders.asScala.get("content-length").map(_.toInt).getOrElse(0)

  private def quit(): Response =
    println("Quitting")
    stop()
    okMsg("")

  private def runningMessage(): Response =
    okMsg("XsltServer is running")

  private def okMsg(msg: String): Response =
    newFixedLengthResponse(Status.OK, MIME_PLAINTEXT, msg)

  private def badRequest(msg: String): Response =
    newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, msg)


