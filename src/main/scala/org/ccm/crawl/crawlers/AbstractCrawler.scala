package org.ccm.crawl.crawlers

import java.io.FileOutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.{ExecutorService, Executors}

import com.mongodb.casbah.{Implicits, MongoConnection}
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.config.ConfigFactory
import org.jsoup.Connection.Response
import org.jsoup.Jsoup
import org.jsoup.select.Elements
import org.ccm.crawl.util.CrawlerKit
import org.ccm.crawl.{Link, Item}
import CrawlerKit._
import org.slf4j.{LoggerFactory, Logger}
import scala.collection.immutable.Map

import scala.annotation.tailrec

class AbstractCrawler(setting: String = null, jsonSetting: String = null) extends Thread {
  private val log: Logger = LoggerFactory.getLogger(this.getClass)

  private val mongoConn = MongoConnection()("sinker")
  private val itemsCollection = mongoConn("items")

  private val failedLinksCollection = mongoConn("broken_links")

  private val rootConfig = ConfigFactory.load()

  private val fsRoot = rootConfig.getString("fs.temp")

  private val rootSttings = rootConfig.getConfig("fetch-settings")

  private val crawlerConfig = if (jsonSetting == null) rootSttings.getConfig(setting) else ConfigFactory.parseString(jsonSetting)
  private val crawlerHost = crawlerConfig.getString("host")
  private val crawlerContextRoot = crawlerConfig.getString("contextRoot")
  private val itemPageExtraParam = crawlerConfig.getString("itemPageExtraParam")

  private val selectorsConfig = crawlerConfig.getConfig("selectors")
  private val navigationComponentSelector = selectorsConfig.getString("navComponent")
  private val linksToItemsSelector = selectorsConfig.getString("linkToItem")
  private val pagingsSelector = selectorsConfig.getString("pagings")

  private val itemSelectorsConfig = crawlerConfig.getConfig("itemSelector")
  private val titleItemSelector = itemSelectorsConfig.getString("title")
  private val priceItemSelector = itemSelectorsConfig.getString("price")
  private val categoryItemSelector = itemSelectorsConfig.getString("category")
  private val subCategoryItemSelector = itemSelectorsConfig.getString("subcategory")
  private val imagesSrcSelector = itemSelectorsConfig.getString("imageSrc")

  private val itemFeaturesSelectorConfig = itemSelectorsConfig.getConfig("featuresSelector")

  private val featureNameSelector = itemFeaturesSelectorConfig.getString("name")
  private val featureValueSelector = itemFeaturesSelectorConfig.getString("value")

  val pool: ExecutorService = Executors.newFixedThreadPool(rootConfig.getInt("threadpool.size"))

  @volatile var itemCounter = 0;

  /**
   * Thread's Overriden Run method.
   */
  override def run(): Unit = {
    log.debug(s"Crawler started to parse $crawlerContextRoot")

    log.debug(s"Host: $crawlerHost")
    log.debug(s"ContextRoot: $crawlerContextRoot")
    getNavigationLinks(crawlerContextRoot)

    log.debug(s"Crawler finished to parse $crawlerContextRoot")
  }

  /**
   * Parse links to navigation elements.
   * @param url
   */
  private def getNavigationLinks(url: String): Unit = {
    def searchFor = findOn(url)
    val navigationElements = searchFor(navigationComponentSelector)
    for(i <- 0 until navigationElements.size) {
      pool.execute(new Runnable {
        override def run(): Unit = {
          val link = Link(directURL(navigationElements.get(i).attr("href")), navigationElements.get(i).text)
          getLinksToItemsInfo(link)
        }
      })
    }
  }

  def verify(exampleUrl: String): Item = parseItemFromUrl(exampleUrl)

  /**
   * Get Lists of Items from page and pagings recursively.
   * @param linkToItems
   */
  @tailrec private def getLinksToItemsInfo(linkToItems: Link): Unit = {
    log.info(linkToItems.toString)
    def searchFor = findOn(linkToItems.url)
    process(searchFor(linksToItemsSelector))
    val pagings: Elements = searchFor(pagingsSelector)
    if(!pagings.isEmpty) {
      val link = directURL(pagings.get(0).child(0).attr("href"))
      log.debug(s">>>> paging found : $link on ${linkToItems.url}")
      if (link == linkToItems.url) {
        log.debug("link == url, returning results")
        return
      }
      getLinksToItemsInfo(Link(link, ""))
    }
  }

  /**
   * Process Links to Items.
   * @param linksToItems
   */
  def process(linksToItems: Elements): Unit = {
    //val builder = itemsCollection.initializeUnorderedBulkOperation
    for(i <- 0 until linksToItems.size) {
      log.debug(linksToItems.get(i).attr("href"))
      saveToDB(parseItemFromUrl(directURL(linksToItems.get(i).attr("href"))))
    }
  }


  /**
   * Parses Item from given URL.
   * @param url
   * @return
   */
  def parseItemFromUrl(url: String): Item = {
    val directUrl = directItemURL(url)
    try {
      def searchFor = findOn(directUrl)
      val title: String = searchFor(titleItemSelector).text
      val price: Double = matchPrice(searchFor, priceItemSelector)
      val category: String = searchFor(categoryItemSelector).text
      val subcategory: String = searchFor(subCategoryItemSelector).text

      val imageSrc: String = searchFor(imagesSrcSelector).attr("src")
      saveImage(imageSrc, title)
      //log.info(s"\nItem's URL : $url, \ntitle : $title \ncategory : $category\nsubcategory: $subcategory\nprice : $price\n")
      itemCounter += 1;
      log.info(s"$itemCounter $title")
      val features: Map[String, String] = parseItemFeatures(searchFor)
      return Item(url, title, category, subcategory, price, features)
    } catch {
      case e: SocketTimeoutException => {
        log.error(s"Exception occured while processing parseItemFromUrl($url)", e)
        saveBrokenLink(Link(url, "Item Parse Error"))
        return null
      }
    }
  }

  /**
   * Saves Link which was parsed with an Exception.
   * @param link
   */
  def saveBrokenLink(link: Link): Unit = {
    failedLinksCollection += MongoDBObject(
      "url" -> link.url,
      "title" -> link.title
    )
  }

  /**
   * Saves Image which was parsed from Html.
   * @param link
   * @param title
   */
  def saveImage(link: String, title: String): Unit = {
    //Open an URL Stream
    val resultImageResponse: Response  = Jsoup.connect(directURL(link)).ignoreContentType(true).execute()

    // output here
    val out: FileOutputStream = (new FileOutputStream(new java.io.File(fsRoot + title.replaceAll("\\/", ""))));
    out.write(resultImageResponse.bodyAsBytes());  // resultImageResponse.body() is where the image's contents are.
    out.close();
  }

  /**
   * Saves item to DB.
   * @param item
   */
  def saveToDB(item: Item): Unit = {
    try {
      itemsCollection += MongoDBObject(
        "host" -> crawlerHost,
        "url" -> item.url,
        "title" -> item.title,
        "category" -> item.category,
        "subcategory" -> item.subcategory,
        "price" -> item.price,
        "features" -> Implicits.map2MongoDBObject(item.features)
      )
    } catch {
      case e: Exception => log.debug("Error While Saving to collection\n", e); return
    }
  }

  /**
   * Items features parsing
   * @param parsingFunction
   * @return
   */
  private def parseItemFeatures(parsingFunction: (String) => Elements): Map[String, String] = {
    val names = parsingFunction(featureNameSelector)
    val values = parsingFunction(featureValueSelector)

    val features = scala.collection.mutable.Map[String, String]()
    for(i <- 0 until names.size) {
      //log.info(s"Name: ${names.get(i).text} Value: ${values.get(i).text}")
      features(names.get(i).text) = values.get(i).text
    }
    return features.toMap
  }

  /**
   * If URL starts with http: - it is formed in proper way.
   * In other case prepends contextroot of site to URL.
   * @param url
   * @return
   */
  def directURL(url: String) = if(url.startsWith("http")) url  else crawlerContextRoot + url

  /**
   * If URL starts with http: - it is formed in proper way.
   * In other case prepends contextroot of site to URL.
   * @param url
   * @return
   */
  def directItemURL(url: String) = if(url.startsWith("http")) url + itemPageExtraParam else crawlerContextRoot + url + itemPageExtraParam

  /**
  * Stops Crawler and releases resources (that includes child Crawlers);
  */
  def shutdown() = {
    log.info(s"Stopping Crawler representing $crawlerHost")
    val a = pool.shutdownNow();
    var size = a.size();
    log.info(s"Size : $size")
    this.stop();
  }
}
