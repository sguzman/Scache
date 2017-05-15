package com.github.sguzman.scache

import java.net.InetSocketAddress
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import scala.language.postfixOps

object Scache {
  def main(args: Array[String]) {
    val server = HttpServer.create(new InetSocketAddress(8000), 0)
    server.createContext("/", new Scache())
    server.setExecutor(null)

    server.start()
    println("Hit Q key to exit...")

    while (true) {
      if ('q' == System.in.read()) {
        server.stop(0)
      }
    }

  }
}

class Scache extends HttpHandler{
  var idx: Int = 0

  def handle(t: HttpExchange) {
    print(t)
    sendResponse(t)
  }

  def print(resq: HttpExchange): Unit ={
    val query = resq.getRequestURI.getQuery
    val path = resq.getRequestURI.getPath
    val headers = resq.getRequestHeaders
    val host = if (headers.containsKey("gyg-host")) headers.get("gyg-host") else ""
    println
    println(s"******************** REQUEST START: [${this.idx}] ********************")
    println
    println("\tRequest Data")
    println(s"\t\tPath")
    println(s"\t\t\t[$path]")
    println(s"\t\tQuery String")
    println(s"\t\t\t[$query]")
    println(s"\t\tHost")
    println(s"\t\t\t[$host]")
    println(s"\t\tHeaders")
    println("\tResponse Body")
    println(s"\t\tData")
    println(s"\t\t\t[]")
    println
    println(s"********************* REQUEST END: [${this.idx}] *********************")
    println
    this.idx += 1
  }

  private def sendResponse(t: HttpExchange) {
    val response = "Ack!"
    t.sendResponseHeaders(200, response.length())
    val os = t.getResponseBody
    os.write(response.getBytes)
    os.close()
  }
}