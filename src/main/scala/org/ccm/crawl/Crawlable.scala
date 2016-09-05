package org.ccm.crawl

/**
 * Created by kuzmentsov@gmail.com on 17.10.15.
 *
 *
 * Trait of all crawlable elements.
 */
sealed trait Crawlable

/**
 * Represents an abstract link
 *
 * @param url
 * @param title
 */
final case class Link(url: String, title: String) extends Crawlable

/**
 * Represents an abstract item
 *
 * @param url
 * @param title
 * @param category
 * @param subcategory
 * @param price
 */
final case class Item(url: String, title: String, category: String, subcategory: String, price: Double, features: scala.collection.immutable.Map[String, String]) extends Crawlable
