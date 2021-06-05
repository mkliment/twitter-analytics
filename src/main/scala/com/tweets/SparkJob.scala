package com.tweets

import org.apache.spark.sql.SparkSession

import com.tweets.storage.{LocalStorage, Storage}

trait SparkJob extends Logging with Serializable {

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder
      .appName(appName)
      .getOrCreate()

    parseAndRun(spark, args)

    def parseAndRun(spark: SparkSession, args: Array[String]): Unit = {
      new UsageOptionParser().parse(args, UsageConfig()) match {
        case Some(config) => run(spark, config, new LocalStorage(spark))
        case None =>
          throw new IllegalArgumentException(
            "arguments provided to job are not valid"
          )
      }
    }
  }

  def run(spark: SparkSession, config: UsageConfig, storage: Storage)

  def appName: String
}
