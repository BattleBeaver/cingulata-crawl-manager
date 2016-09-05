import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import com.typesafe.config.ConfigFactory

import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Max-Age`}
import akka.http.scaladsl.server.Directives._

import org.ccm.crawl.crawlers._

import org.ccm.crawl.Item
import org.ccm.conf.cors.CorsSupport

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props

// collect your json format instances into a support trait:
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val itemFormat = jsonFormat6(Item)
}

object AkkaHttpMicroservice extends JsonSupport with CorsSupport{

  val config = ConfigFactory.load()

  implicit val actorSystem = ActorSystem("system")
  //val helloActor = actorSystem.actorOf(Props[Crawler])

  implicit val actorMaterializer = ActorMaterializer()

  override val corsAllowOrigins: List[String] = List("*")

  override val corsAllowedHeaders: List[String] = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")

  override val corsAllowCredentials: Boolean = true

  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials)
  )

  var crawler: AbstractCrawler = null;


  val routes =
    cors {
      path("crawlers" / IntNumber / "status") { id =>
          get { ctx =>
            ctx.complete("status")
          }
      } ~
      path("crawlers" / IntNumber / "start") { id =>
          get { ctx =>
            crawler = new AbstractCrawler("allo.ua")
            crawler.start()
            ctx.complete("")
          }
      } ~
      path("crawlers" / IntNumber / "stop") { id =>
          get { ctx =>
            crawler.shutdown();
            ctx.complete("Received " + ctx.request.method.name + " stop request for crawler " + id)
          }
      } ~
      path("crawlers" / "verify") {
        post {
          entity(as[String]) { json =>
            val _crawler = new AbstractCrawler(jsonSetting = json);
            val testItem = _crawler.verify("http://allo.ua/ru/products/mobile/apple-iphone-se-16gb-space-gray.html");
            complete(testItem)
          }
        }
      }
    }


    def main(args: Array[String]) = {
      Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port"))

      println("server started at " + config.getInt("http.port"))
    }
}
