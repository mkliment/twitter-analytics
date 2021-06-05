package com.tweets

import com.tweets.Functions.parseTwitterTs
import com.tweets.storage._
import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Duration, Seconds, StreamingContext}
import org.apache.spark.streaming.dstream.DStream._

import java.sql.Date
import java.text.SimpleDateFormat

object HappiestHashtagsWindowBased extends SparkJob with TwitterStream {

  override def appName: String = "HappiestHashtagsWindowBased"

  override def run(spark: SparkSession, config: UsageConfig, storage: Storage): Unit = {

    val topNSamples = config.topNSamples.getOrElse("10")

    val triggerIntervalSeconds = config.triggerIntervalSeconds.getOrElse(150L)
    val windowIntervalSeconds =
      config.windowIntervalSeconds.getOrElse("600")

    val filterTweets = Seq(s"#")

    @transient val streamingContext =
      new StreamingContext(spark.sparkContext, Seconds(triggerIntervalSeconds))

    val tweetsStream = getTwitterStream(spark, streamingContext, filterTweets)

    val tweetsDs = tweetsStream
      .filter(_.getLang() == "en")
      .map(
        status =>
          TweetHashtagsSchema(
            status.getText,
            status.getHashtagEntities.map(_.getText()), // we use hashtag entities
            status.getCreatedAt.toInstant.toString
          )
      )

    val result = transform(spark, tweetsDs, windowIntervalSeconds, triggerIntervalSeconds)

    result.foreachRDD(rdd => {
      if (!rdd.isEmpty) {

        val topList = rdd.take(topNSamples.toInt).distinct
        LOGGER.info(s"******************************************")
        LOGGER.info(s"Found ${topList.length} happiest hashtags on EN language:")
        topList.foreach { case (count, hourHashtag) => LOGGER.info(s"$hourHashtag, $count") }
      }
    })

    streamingContext.start()
    streamingContext.awaitTermination()

  }

  def transform(
      spark: SparkSession,
      tweetsRdd: DStream[TweetHashtagsSchema],
      windowIntervalSeconds: String,
      triggerIntervalSeconds: Long
    ): DStream[(Long, String)] = {

    val formatter = new SimpleDateFormat("yyyy-MM-dd HH")
    // Recompute the top hashtags every triggerIntervalSeconds second
    val slideInterval = new Duration(triggerIntervalSeconds * 1000)

    // Compute the top hashtags for the last windowIntervalSeconds seconds
    val windowLength = new Duration(windowIntervalSeconds.toInt * 1000)

//    val smileEmojiPattern = raw"😀|😇|😎|☺️|🙂".mkString.r
    val smileEmojiPattern = raw"\uD83D\uDE00|\uD83D\uDE06|\uD83D\uDE07|\uD83D\uDE0E|\uD83D\uDE42|\uD83D\uDEC1|\uD83D\uDE05".mkString.r

    val windowedCounts = tweetsRdd
      .map(
        t =>
          TweetTextCountSchema(
            t.text,
            formatter.format(new Date(parseTwitterTs(t.createdAt).getTime)).toString,
            t.hashtags,
            smileEmojiPattern.findAllIn(t.text).length
          )
      )
      .filter(_.countSmiles > 0L)
      .flatMap(t => {
        val hashtags = t.hashtags
        hashtags.map((t.hourTs,_,t.countSmiles))
      })
      .map(t => (t._1 + s", " + t._2, t._3))
      .reduceByKeyAndWindow((x: Long, y: Long) => x, windowLength, slideInterval) // we need distinct value per Key
      .map { case (hourHashtag, count) => (count, hourHashtag) } // switch key with value for sorting to work
      .transform(_.sortByKey(false))

    windowedCounts

  }

}
