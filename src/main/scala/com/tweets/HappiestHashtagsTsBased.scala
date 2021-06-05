package com.tweets

import com.tweets.Functions.{parseTwitterTs, selectSchema}
import com.tweets.storage._
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{current_timestamp, date_trunc, explode, expr, lit, regexp_extract, regexp_replace, row_number, size, split, trim, udf}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object HappiestHashtagsTsBased extends SparkJob with TwitterStream {

  override def appName: String = "HappiestHashtagsTsBased"

  override def run(spark: SparkSession, config: UsageConfig, storage: Storage): Unit = {

    import spark.implicits._

    val topNSamples = config.topNSamples.getOrElse("10")
    val triggerIntervalSeconds = config.triggerIntervalSeconds.getOrElse(150L)

    val outputPath = s"/usr/local/data/prod/output/event-ts-based"
    val outputFile = s"out-result.dat"

    val filterTweets = Seq(s"#", s":)")

    @transient val streamingContext =
      new StreamingContext(spark.sparkContext, Seconds(triggerIntervalSeconds))

    val tweetsStream = getTwitterStream(spark, streamingContext, filterTweets)

    tweetsStream.foreachRDD { rdd =>
      if (!rdd.isEmpty) {

        val tweetsDs = rdd
          .map(
            status =>
              TweetHashtagsSchema(
                status.getText,
                status.getHashtagEntities.map(_.getText()), // we use hashtag entities
                status.getCreatedAt.toInstant.toString
              )
          )
          .toDF("text", "hashtags", "createdAt")
          .as[TweetHashtagsSchema]

        val result = transform(
          spark,
          tweetsDs,
          topNSamples
        )

        LOGGER.info(s"Found ${result.length} happiest hashtags for batch")

        // let's create only 1 csv (instead writeToCsv) file and append content of all windows
        // TODO: check writeToCsv with coalesce(1)
        storage.writeStringToFile(
          outputPath,
          outputFile,
          result.mkString("\n"),
          append = true
        )
      }

    }

    streamingContext.start()
    streamingContext.awaitTermination()

  }

  def transform(spark: SparkSession, tweets: Dataset[TweetHashtagsSchema], topNSamples: String): List[String] = {

    import spark.implicits._

    val wTopN = Window
      .partitionBy("hourTs") // we select top happiest hashtags for each sliding window
      .orderBy($"countSmiles".desc, $"hourTs".desc)

    val formatTs = udf(parseTwitterTs(_: String): java.sql.Timestamp)

    val aggDf = tweets
      .filter($"text".contains(s":)"))
      .withColumn("createdAtTs", formatTs(lit($"createdAt")))
      .filter($"createdAtTs" >= (current_timestamp() - expr("INTERVAL 1 HOURS")))
      .withColumn("hourTs", date_trunc("Hour", $"createdAtTs"))
      .withColumn("hashtag", explode($"hashtags"))
      .select($"text", $"hashtag", $"hourTs")
      .distinct()
      .withColumn("textListRemovedSmiles", split($"text", s":\\)"))
      .withColumn("countSmiles", expr("size(textListRemovedSmiles)-1"))
      .select($"hourTs", $"hashtag", $"countSmiles")
      .distinct()
      .withColumn(
        "rn",
        row_number().over(wTopN)
      )
      .filter($"rn" <= topNSamples)
      .drop("rn")

    val outputDs =
      selectSchema[TweetCountSchema](aggDf)
        .as[TweetCountSchema]

    // we use collect but we are sure that we don't have big data so will not crash the spark driver
    val output =
      outputDs
        .collect()
        .map { row: TweetCountSchema =>
          val hourTs = row.hourTs
          val hashtag = row.hashtag
          val countSmiles = row.countSmiles
          s"$hourTs, $hashtag, $countSmiles"
        }
        .toList // TODO: implement orderBy countSmiles before save it

    output

  }

}
