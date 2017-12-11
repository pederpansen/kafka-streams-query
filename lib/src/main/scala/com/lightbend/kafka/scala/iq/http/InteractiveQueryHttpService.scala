package com.lightbend.kafka.scala.iq
package http

import akka.actor.ActorSystem

import akka.http.scaladsl.server.Directives
import Directives._
import akka.http.scaladsl.Http

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{ ExceptionHandler, Route }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow

import io.circe.generic.auto._
import io.circe.syntax._

import org.apache.kafka.streams.state.HostInfo

import scala.concurrent.{ Future, ExecutionContext}
import scala.util.{ Try, Success, Failure }

import com.typesafe.scalalogging.LazyLogging


abstract class InteractiveQueryHttpService(
  hostInfo: HostInfo,
  implicit val actorSystem: ActorSystem,
  implicit val actorMaterializer: ActorMaterializer,
  implicit val ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with LazyLogging {

  val myExceptionHandler = ExceptionHandler {
    case ex: Exception =>
      extractUri { uri =>
        logger.error(s"Request to $uri could not be handled normally", ex)
        complete(HttpResponse(InternalServerError, entity = "Request Failed!"))
      }
  }


  // define the routes
  val routes: Flow[HttpRequest, HttpResponse, Any]
  var bindingFuture: Future[Http.ServerBinding] = _


  // start the http server
  def start(): Unit = {
    bindingFuture = Http().bindAndHandle(routes, hostInfo.host, hostInfo.port)

    bindingFuture.onComplete {
      case Success(serverBinding) =>
        logger.info(s"Server bound to ${serverBinding.localAddress} ")

      case Failure(ex) =>
        logger.error(s"Failed to bind to ${hostInfo.host}:${hostInfo.port}!", ex)
        actorSystem.terminate()
    }
  }


  // stop the http server
  def stop(): Unit = {
    logger.info("Stopping the http server")
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => actorSystem.terminate())
  }
}

