package com.tweets

import org.apache.spark.sql.{DataFrame, Dataset}

import java.sql.Timestamp
import java.text.SimpleDateFormat
import scala.language.implicitConversions
import scala.reflect.runtime.universe.{weakTypeOf, MethodSymbol, WeakTypeTag}

object Functions {

  def columnsFromSchema[T: WeakTypeTag]: List[String] = {
    // create list of columns for DFs from given case class
    weakTypeOf[T].members.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }.toList
      .map(_.name.toString)
  }

  def selectSchema[T: WeakTypeTag](df: Dataset[_]): DataFrame = {
    // select columns derived from case class
    val cols = columnsFromSchema[T]
    df.select(cols.head, cols.tail: _*)
  }

  def selectSortedSchema[T: WeakTypeTag](df: Dataset[_]): DataFrame = {
    // select ordered columns derived from case class
    val cols = columnsFromSchema[T].sortWith(_ > _)
    df.select(cols.head, cols.tail: _*)
  }

  def parseTwitterTs(dt: String): Timestamp = {
//    val ts = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy").parse(dt).getTime
    val ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").parse(dt).getTime
    new java.sql.Timestamp(ts)
  }
}
