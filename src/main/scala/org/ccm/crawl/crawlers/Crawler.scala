// import akka.actor.Actor
// import akka.actor.ActorSystem
// import akka.actor.Props
//
// import org.ccm.crawl.crawlers.AbstractCrawler
//
// class Crawler extends Actor {
//   var status: String = "stopped"
//   var crawler: AbstractCrawler = null
//
//   def receive = {
//     case "start"  => {
//       crawler = new AbstractCrawler("allo.ua")
//       crawler.start()
//       status = "started"
//     }
//     case "stop"   => {
//       crawler.shutdown();
//       context.stop(self)
//       status = "stopped"
//     }
//     case "status" => println(status)
//     case _        => println("huh?")
//   }
// }
