package com.github.sguzman.scache

import java.io.{FileOutputStream, PrintWriter}
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpOriginRange, RawHeader}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import org.json.{JSONObject, JSONTokener}
import org.pmw.tinylog.Logger

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService}
import scala.concurrent.duration.Duration
import scala.io.Source

object Scache extends HttpApp {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  val filename = "./data.db"
  var cache: Map[String, String] = this.getCache
  var reqCounter: Long = 0

  var settings = CorsSettings.defaultSettings.copy(allowGenericHttpRequests = true, allowCredentials = false, allowedOrigins = HttpOriginRange.*)

  def route: Route =  {
    CorsDirectives.cors(settings) {
      extractRequest { request =>
        val content = request.headers.filter(_.name == "Content-Language").head.value
        val headers = new JSONObject(new JSONTokener(content))
        val body = Await.result(request.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String), Duration.Inf)
        val key = s"${request.uri.toString}$content$body"

        if (cache.contains(key)) {
          complete(HttpResponse(entity = HttpEntity(cache.getOrElse(key, ""))))
        } else {

          val path = request.uri.path.toString
          var querystring = request.uri.rawQueryString.getOrElse("")
          if (!querystring.isEmpty) {
            querystring = "?" + querystring
          }

          val host = headers.getString("Host")
          val httpPrep = HttpRequest(HttpMethods.GET, s"https://$host$path$querystring", Nil, HttpEntity(body), HttpProtocols.`HTTP/1.1`)
          headers.keySet.forEach(h => httpPrep.withHeaders(RawHeader(h, headers.getString(h))))

          val result = Await.result(Http().singleRequest(httpPrep), Duration.Inf)
          val entityBody = Await.result(result.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String), Duration.Inf)
          this.metaPrint(host, path, querystring, body, content, result, entityBody)

          if (result.status.intValue() == 200) {
            cache += (key -> entityBody)

            this.setCache()
          }

          complete(HttpResponse(entity = HttpEntity(entityBody)))
        }
      }
    }
  }

  def getCache: Map[String,String] = {
    new FileOutputStream(this.filename, true).close()
    val fileInnards = Source.fromFile(this.filename).mkString
    if (fileInnards.isEmpty) {
      return Map[String,String]()
    }

    upickle.default.read[Map[String,String]](fileInnards)
  }

  def setCache(): Unit = {
    new PrintWriter(filename) {
      write(upickle.default.write[Map[String,String]](Scache.cache))
      close()
    }
  }

  def metaPrint(host: String, path: String, qs: String, body: String, headers: String, resp: HttpResponse, entityBody: String): Unit = {
    Logger.info(
      s"""
         |\t PROXY REQUEST - [${this.reqCounter}]
         |\t\tHost
         |\t\t\t$host
         |\t\tPath
         |\t\t\t$path
         |\t\tQuery String
         |\t\t\t$qs
         |\t\tBody
         |\t\t\t$body
         |\t\tHeaders
         |\t\t\t$headers
         |
         |\t---------------------------
         |
         |\t\tStatus Code
         |\t\t\t${resp.status}
         |\t\tContent Length
         |\t\t\t${resp.entity.contentLengthOption.getOrElse(-1)}
         |\t\tContent Type
         |\t\t\t${resp.entity.contentType.toString}
         |\t\tResponse Body
         |\t\t\t$entityBody
         |\t PROXY REQUEST - [${this.reqCounter}]
       """.stripMargin
    )
    this.reqCounter += 1
  }

  def main(args: Array[String]): Unit = try {
    this.startServer("localhost", args.head.toInt)
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      System.exit(1)
  }
}