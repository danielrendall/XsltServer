package uk.co.danielrendall.xsltserver

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.*
import fi.iki.elonen.NanoHTTPD.Response.Status
import uk.co.danielrendall.xsltserver.Exceptions.*

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.math.BigInteger
import java.security.MessageDigest
import java.util
import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{Templates, TransformerFactory}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsScala}
import scala.util.{Failure, Success, Try, Using}

/**
 * The server itself
 */
class XsltServerApp(port: Int) extends NanoHTTPD(port):

  object Constants:
    val QUIT = "_quit"
    val LIST = "_list"
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
    if (first == Constants.LIST) {
      okMsg(nameToHash.keySet.toList.sorted.mkString("\n"))
    } else {
      okMsg("GET " + first)
    }

  private def post(session: IHTTPSession, first: String, rest: List[String]) =
    if (first.startsWith("_")) {
      badRequest("Identifiers starting with underscores are reserved")
    } else {
      (for {
        byteArray <- getUploadedBytes(session)
        _ <- if (byteArray.nonEmpty) Success(()) else Failure(NoDocumentSuppliedException)
        params <- Try(getReducedParameters(session.getParameters))
        result <- process(first, byteArray, params)
      } yield result) match {
        case Success(result) =>
          newFixedLengthResponse(Status.OK, "text/xml", new ByteArrayInputStream(result), result.length)
        case Failure(ex) =>
          badRequest(ex.getMessage)
      }
    }


  private def put(session: IHTTPSession, first: String, rest: List[String]) =
    if (first.startsWith("_")) {
      badRequest("Identifiers starting with underscores are reserved")
    } else {
      (for {
        byteArray <- getUploadedBytes(session)
        _ <- if (byteArray.nonEmpty) saveTemplates(first, byteArray) else Success(())
        _ <- saveParams(first, session.getParameters)
      } yield ()) match {
        case Failure(ex) =>
          badRequest(ex.getMessage)
        case Success(_) =>
          okMsg(s"Put $first")
      }
    }

  private def delete(session: IHTTPSession, first: String, rest: List[String]) =
    okMsg("DELETE " + first)

  private def getUploadedBytes(session: IHTTPSession): Try[Array[Byte]] =
    val bodySize: Int = getBodySize(session)
    if (bodySize >  Constants.MAX_BODY_SIZE)
      Failure(RequestTooBigException(bodySize))
    else
      Try {
        val baos = new ByteArrayOutputStream(bodySize)
        StreamUtils.copy(session.getInputStream(), baos, bodySize)
        baos.toByteArray
      }

  private def saveTemplates(name: String, code: Array[Byte]): Try[Unit] =
    Try {
      val hash = String.format("%032x", new BigInteger(1, MessageDigest.getInstance("MD5").digest(code)))
      nameToHash.put(name, hash)
      if (!hashToTemplates.contains(hash)) {
        hashToTemplates.put(hash, tf.newTemplates(new StreamSource(new ByteArrayInputStream(code))))
      }
    }

  private def saveParams(name: String, params: util.Map[String, util.List[String]]): Try[Unit] =
    Try {
      nameToDefaultParams.put(name, getReducedParameters(params))
    }

  private def getReducedParameters(params: util.Map[String, util.List[String]]): Map[String, String] =
    // Assume that the map is single valued for all params
    params.asScala.map { case (name, values) => name -> values.asScala.headOption.getOrElse("")}.toMap

  private def getBodySize(session: IHTTPSession): Int =
    session.getHeaders.asScala.get("content-length").map(_.toInt).getOrElse(0)

  private def process(name: String, document: Array[Byte], params: Map[String, String]): Try[Array[Byte]] =
    for {
      templates <- getTemplates(name)
      defaults <- getDefaultParameters(name)
      finalParams: Map[String, String] = defaults ++ params
      result <- process(templates, document, finalParams)
    } yield result

  private def process(templates: Templates, document: Array[Byte], params: Map[String, String]): Try[Array[Byte]] =
    Try {
      val baos = new ByteArrayOutputStream()
      val result = new StreamResult(baos)
      val transformer = templates.newTransformer()
      params.foreach { case (name, value) => transformer.setParameter(name, value) }
      transformer.transform(new StreamSource(new ByteArrayInputStream(document)), result)
      baos.toByteArray
    }

  private def getTemplates(name: String): Try[Templates] =
    Try {
      (for {
        hash <- nameToHash.get(name)
        templates <- hashToTemplates.get(hash)
      } yield templates).getOrElse(throw NoTemplatesStoredException(name))
    }

  private def getDefaultParameters(name: String): Try[Map[String, String]] =
    Try {
      nameToDefaultParams.getOrElse(name, Map.empty)
    }

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


