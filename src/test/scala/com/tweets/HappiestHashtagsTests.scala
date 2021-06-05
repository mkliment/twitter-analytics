package com.tweets

import com.tweets.storage._
import com.tweets.test.SharedSparkSession.spark

import org.apache.spark.rdd._
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.{Seconds, StreamingContext}

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.Calendar
import java.text.SimpleDateFormat
import scala.collection.mutable

class HappiestHashtagsTests extends AnyFunSuite with Matchers with Logging with TwitterStream {

  //TODO: all tests on one place, it is hack to work temporary unit tests during build
  // fix issue "Only one StreamingContext may be started in this JVM."

  spark.sparkContext.setLogLevel("INFO")

  test("TsBased: should select top N happiest hashtags sent in the past hour excluding late tweets") {
    val topNSamples = "4"
    val triggerIntervalSeconds = 10

    import spark.implicits._

    @transient val streamingContext = new StreamingContext(spark.sparkContext, Seconds(triggerIntervalSeconds))

    // In order to have expected output from this test we need to have these input parameters
    topNSamples shouldBe "4"

    val StandardDateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    val nowDateTime = Calendar.getInstance().getTime
    val nowMinus2DateTime = Calendar.getInstance()
    nowMinus2DateTime.add(Calendar.HOUR, -2)
    val nowDt = StandardDateTimeFormatter.format(nowDateTime).toString
    val nowMinusHourDt = StandardDateTimeFormatter.format(nowMinus2DateTime.getTime).toString

    val tweetEventsBatch = Seq(
      TweetHashtagsSchema("not happy status", Array("someA0", "someA1", "someA2"), nowDt),
      TweetHashtagsSchema("happy1 status :)", Array("someI1", "someB1", "someB2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someC0", "someC1", "someC2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someD0", "someD1", "someD2"), nowDt),
      TweetHashtagsSchema("happy3 :) status :):)", Array("someE0", "someE1", "someE2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someF0", "someF1", "someF2"), nowDt),
      TweetHashtagsSchema("happy5 status :):):):):)", Array("someG0", "someG1", "someG2"), nowMinusHourDt), // this should be excluded
      TweetHashtagsSchema("not happy status", Array("someH0", "someH1", "someH2"), nowDt),
      TweetHashtagsSchema(":)happy3 status :):)", Array("someI0", "someI1", ":):):)"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someJ0", "someJ1", "someJ2"), nowDt),
      TweetHashtagsSchema("happy2 :) status:)", Array("someK0", "someK1", "someI1"), nowDt),
      TweetHashtagsSchema("happy1 status:)", Array("someL0", "someI1", "someL2"), nowDt),
        TweetHashtagsSchema("happy6 :) status :):):):):)",Array("someS1"), nowDt)
    )

    val queueRDD = mutable.Queue[RDD[TweetHashtagsSchema]]()
    val tweetsStream = streamingContext.queueStream(queueRDD)

    tweetsStream.foreachRDD { rdd =>
      if (!rdd.isEmpty) {

        val tweetsDs = rdd
          .toDF("text", "hashtags", "createdAt")
          .as[TweetHashtagsSchema]

        tweetsDs.show(false)

        val result = HappiestHashtagsTsBased
          .transform(
            spark,
            tweetsDs,
            topNSamples
          )
        LOGGER.info(s"hourTs, hashtag, count")
        LOGGER.info(s"$result")

        val firstResult = result.head.split(",").map((_).trim)
        firstResult(2) shouldBe "6"
        firstResult(1) shouldBe "someS1"

      }
    }

    streamingContext.start()

    queueRDD.synchronized { queueRDD += streamingContext.sparkContext.makeRDD(tweetEventsBatch) }

    streamingContext.awaitTerminationOrTimeout(20 * 1000)
    streamingContext.stop(false, true)

  }

  test("WindowBased: should select top N happiest hashtags (with at least 1 smile emoji) per sliding window") {

    val topNSamples = "10"

    val triggerIntervalSeconds = 2
    val windowIntervalSeconds = "16"

    val testRun = 4

    val outputPath = s"data/test/out/event-window-based"
    val outputFile = s"out-results-window.dat"
    @transient val streamingContext = new StreamingContext(spark.sparkContext, Seconds(triggerIntervalSeconds))

    // In order to have expected output from this test we need to have these input parameters
    topNSamples shouldBe "10"
    triggerIntervalSeconds shouldBe 2
    windowIntervalSeconds shouldBe "16"
    testRun shouldBe 4 // our test os checking this run

    val StandardDateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

    val nowDateTime = Calendar.getInstance().getTime
    val nowMinus2DateTime = Calendar.getInstance()
    nowMinus2DateTime.add(Calendar.HOUR, -2)
    val nowDt = StandardDateTimeFormatter.format(nowDateTime).toString
    val nowMinusHourDt = StandardDateTimeFormatter.format(nowMinus2DateTime.getTime).toString

    val tweetEvents1 = Seq(
      TweetHashtagsSchema("not happy status", Array("someA0", "someA1", "someA2"), nowDt),
      TweetHashtagsSchema(s"happy1 status \uD83D\uDE42", Array("someI1", "someB1", "someB2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someC0", "someC1", "someC2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someD0", "someD1", "someD2"), nowDt)
    )
    val tweetEvents2 = Seq(
      TweetHashtagsSchema(s"happy3 status \uD83D\uDE42\uD83D\uDE42some\uD83D\uDE42", Array("someE0", "someE1", "someE2"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someF0", "someF1", "someF2"), nowDt),
      TweetHashtagsSchema(
        s"happy5 status \uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00\uD83D\uDE42",
        Array("someG2"),
        nowMinusHourDt
      ),
      TweetHashtagsSchema("not happy status", Array("someH0", "someH1", "someH2"), nowDt)
    )
    val tweetEvents3 = Seq(
      TweetHashtagsSchema(s"happy3 \uD83D\uDE00 \uD83D\uDE00 \uD83D\uDE00 status", Array("someI0", "someI1", ":):):)"), nowDt),
      TweetHashtagsSchema("not happy status", Array("someJ0", "someJ1", "someJ2"), nowDt),
      TweetHashtagsSchema(s"happy2 \uD83D\uDE00 some \uD83D\uDE00 status", Array("someK0", "someK1", "someI1"), nowDt),
      TweetHashtagsSchema(s"happy1 status \uD83D\uDE00", Array("someL0", "someI1", "someL2"), nowDt)
    )

    val queueRDD = mutable.Queue[RDD[TweetHashtagsSchema]]()
    val tweetsStream = streamingContext.queueStream(queueRDD)

    val tweetsDs = tweetsStream
      .map(
        status =>
          TweetHashtagsSchema(
            status.text,
            status.hashtags,
            status.createdAt
          )
      )
    val result = HappiestHashtagsWindowBased.transform(spark, tweetsDs, windowIntervalSeconds, triggerIntervalSeconds)

    var i = 0
    result.foreachRDD(rdd => {
      if (!rdd.isEmpty) {
        i += 1
        val topList = rdd.take(topNSamples.toInt).distinct
        LOGGER.info(s"******************************************")
        LOGGER.info(s"Found ${topList.length} happiest hashtags:")
        topList.foreach { case (count, hourHashtag) => LOGGER.info(s"$hourHashtag, $count") }
        if (i === testRun) {
          LOGGER.info(s"***** We test if results for ${testRun}th rdd are correct")
          topList(0)._1 shouldBe 5
          topList(0)._2 should (endWith("someG2"))
        }
      }
    })
    streamingContext.start()

    queueRDD.synchronized {
      queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents1)
    }
    Thread.sleep(2000)
    queueRDD.synchronized {
      queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents2)
    }
    Thread.sleep(2000)
    queueRDD.synchronized {
      queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents3)
    }
    Thread.sleep(2000)

    streamingContext.awaitTerminationOrTimeout(20 * 1000) // TODO: check how to make this to not die before test is finished
    streamingContext.stop(false, true)

  }

}
