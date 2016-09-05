package org.ccm.crawl.util

import java.net.SocketTimeoutException

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.slf4j.{Logger, LoggerFactory}

import scala.annotation.tailrec
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

/**
 * Created by kuzmentsov@gmail.com on 17.10.15.
 */
object CrawlerKit {

  private val priceRegEx: Regex ="[^\\d]".r
  private val log: Logger = LoggerFactory.getLogger("CrawlerKit")

  /**
   * Getting content of document by it's URL, adding UserAgent parameter.
   * @param url
   * @param timeout
   * @return
   */
  @throws(classOf[SocketTimeoutException])
  @tailrec def documentOf(url: String, timeout: Int = 5000): Document = {
    val document = Try(Jsoup.connect(url).timeout(timeout).userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2490.86 Safari/537.36").get())
    document match {
      case Success(doc) => doc
      case Failure(e) => {
        if (timeout > 15000) {
          log.error(s"Attempt to get contents of $url within 15 sec timeout was unsuccessful:\n $e")
          throw new SocketTimeoutException()
        }
        documentOf(url, timeout + 1000)
      }
    }
  }

  /**
   * Curried function to parse several patterns from one source.
   * @param url
   * @return
   */
  def findOn(url: String): (String) => Elements = {
    (selector: String) => documentOf(url).select(selector)
  }

  /**
   * Get price from string-represented, unformatted value.
   * @param f
   * @param pattern
   * @return
   */
  def matchPrice(f: (String)  => Elements, pattern: String): Double = {
    Option(f(pattern).first) match {
      case Some(element) => priceFromString(element.text)
      case None => -0.1d
    }
  }

  /**
   * Get price from string-represented, unformatted value.
   * @param priceString
   * @return
   */
  def priceFromString(priceString: String): Double = {
    try {
      priceRegEx.replaceAllIn(priceString, "").toDouble
    } catch {
      case e: NumberFormatException => -0.1d
    }
  }
}
