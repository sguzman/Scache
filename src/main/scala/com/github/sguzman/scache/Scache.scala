package com.github.sguzman.scache

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

// Server definition
object Scache extends HttpApp {
  implicit val system = ActorSystem("system")
  implicit val materializer = ActorMaterializer()
  implicit val executor: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))


  var reqCounter: Long = 0

  def route: Route =  {
    extractUri { uri =>
      entity(as[String]) { body =>
        val path = uri.path.toString
        val qs = Map[String,String](uri.rawQueryString.getOrElse("").split('&').map(_.split('=')).map(s => (s(0), s(1))): _*)
        val host = qs.getOrElse("gyg-host", "")
        val querystring = qs.getOrElse("gyg-querystring", "")
        val rawHeaders = qs.filterKeys(_.startsWith("gyg-header-")).map({case (k,v) => RawHeader(StringUtils.substringAfter(k, "gyg-header-").capitalize, v)}).toList

        val httpPrep = HttpRequest(HttpMethods.GET, s"https://$host$path?$querystring").withEntity(body)
        rawHeaders.foreach(httpPrep.withHeaders(_))

        val result = Await.result(Http().singleRequest(httpPrep), Duration.Inf)
        this.metaPrint(host, path, querystring, body, rawHeaders, result)
        complete(result)
      }
    }
  }

  def metaPrint(host: String, path: String, qs: String, body: String, headers: List[RawHeader], resp: HttpResponse): Unit = {
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
         |\t\t\t${Await.result(resp.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String), Duration.Inf)}
         |\t PROXY REQUEST - [${this.reqCounter}]
       """.stripMargin
    )
    this.reqCounter += 1
  }

  def main(args: Array[String]): Unit = this.startServer("localhost", 8080)
}