package com.github.sguzman.scache

import java.io.{FileOutputStream, PrintWriter}
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.apache.commons.lang3.StringUtils
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

  def route: Route =  {
    extractUri { uri =>
      entity(as[String]) { body =>
        if (cache.contains(uri.toString)) {
          complete(HttpResponse(entity = HttpEntity(cache.getOrElse(uri.toString, ""))))
        } else {
          val path = uri.path.toString
          val qs = Map[String,String](uri.rawQueryString.getOrElse("").split('&').map(_.split('=')).map(s => (s(0), s(1))): _*)
          val host = qs.getOrElse("gyg-host", "")
          val querystring = qs.getOrElse("gyg-querystring", "")
          val rawHeaders = qs.filterKeys(_.startsWith("gyg-header-")).map({case (k,v) => RawHeader(StringUtils.substringAfter(k, "gyg-header-").capitalize, v)}).toList

          val httpPrep = HttpRequest(HttpMethods.GET, s"https://$host$path?$querystring").withEntity(body)
          rawHeaders.foreach(httpPrep.withHeaders(_))

          val result = Await.result(Http().singleRequest(httpPrep), Duration.Inf)
          val entityBody = Await.result(result.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String), Duration.Inf)
          this.metaPrint(host, path, querystring, body, rawHeaders, result, entityBody)

          if (result.status.isSuccess) {
            cache += (uri.toString -> entityBody)

            this.setCache()
          }

          complete(result)
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

  def metaPrint(host: String, path: String, qs: String, body: String, headers: List[RawHeader], resp: HttpResponse, entityBody: String): Unit = {
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
         |\t\t\t${headers.mkString("\n")}
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
    this.startServer("localhost", args(0).toInt)
  } catch {
    case _: Throwable => System.exit(1)
  }
}