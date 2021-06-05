package com.tweets

import org.apache.spark.sql.SQLContext
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.rdd._

import java.util.Calendar
import java.text.SimpleDateFormat
import scala.collection.mutable

import com.tweets.storage._
import com.tweets.test.SharedSparkSession.spark

class HappiestHashtagsTsBasedTestIT extends AnyFunSuite with Matchers with Logging {

  spark.sparkContext.setLogLevel("ERROR")

  test("should save on storage top N happiest hashtags on each batch") {
    val topNSamples = "4"
    val triggerIntervalSeconds = 2
    val outputPath = s"data/test/out/event-ts-based"
    val outputFile = s"out-results-ts.dat"

    implicit val sqlContext: SQLContext = spark.sqlContext
    import spark.implicits._
    val storage = new LocalStorage(spark)

    @transient val streamingContext = new StreamingContext(spark.sparkContext, Seconds(triggerIntervalSeconds))

    val StandardDateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val nowDateTime = Calendar.getInstance().getTime
    val nowMinus2DateTime = Calendar.getInstance()
    nowMinus2DateTime.add(Calendar.HOUR, -2)
    val nowDt = StandardDateTimeFormatter.format(nowDateTime).toString
    val nowMinusHourDt = StandardDateTimeFormatter.format(nowMinus2DateTime.getTime).toString

    val tweetEvents1 = Seq(
      TweetHashtagsSchema("not happy status", Array("someA0", "someA1", "someA2"), nowDt),
      TweetHashtagsSchema("happy1 status :)", Array("someI1", "someB1", "someB2"), nowDt),
      TweetHashtagsSchema("not happy status",Array("someC0", "someC1", "someC2"), nowDt),
      TweetHashtagsSchema("not happy status",Array("someD0", "someD1", "someD2"), nowDt)
    )
    val tweetEvents2 = Seq(
      TweetHashtagsSchema("happy3 :) status :):)",Array("someE0", "someE1", "someE2"), nowDt),
      TweetHashtagsSchema("not happy status",Array("someF0", "someF1", "someF2"), nowDt),
      TweetHashtagsSchema("happy5 status :):):):):)",Array("someG0", "someG1", "someG2"), nowMinusHourDt),
      TweetHashtagsSchema("not happy status",Array("someH0", "someH1", "someH2"), nowDt)
    )
    val tweetEvents3 = Seq(
      TweetHashtagsSchema(":)happy3 status :):)",Array("someI0", "someI1", ":):):)"), nowDt),
      TweetHashtagsSchema("not happy status",Array("someJ0", "someJ1", "someJ2"), nowDt),
      TweetHashtagsSchema("happy2 :) status:)",Array("someK0", "someK1", "someI1"), nowDt),
      TweetHashtagsSchema("happy6 :) status :):):):):)",Array("someS1"), nowDt)
    )

    val queueRDD = mutable.Queue[RDD[TweetHashtagsSchema]]()
    val tweetsStream = streamingContext.queueStream(queueRDD)

    tweetsStream.foreachRDD { rdd =>
      if (!rdd.isEmpty) {

        val tweetsDs = rdd
          .toDF("text","hashtags", "createdAt")
          .as[TweetHashtagsSchema]

        val result = HappiestHashtagsTsBased
          .transform(
            spark,
            tweetsDs,
            topNSamples
          )
        LOGGER.info(s"hourTs, hashtag, count")
        LOGGER.info(s"$result")

        storage.writeStringToFile(
          outputPath,
          outputFile,
          result.mkString("\n"),
          append = true
        )
      }

    }

    streamingContext.start()

    queueRDD.synchronized { queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents1) }
    Thread.sleep(2000)
    queueRDD.synchronized { queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents2) }
    Thread.sleep(2000)
    queueRDD.synchronized { queueRDD += streamingContext.sparkContext.makeRDD(tweetEvents3) }
    Thread.sleep(2000)

    streamingContext.awaitTerminationOrTimeout(60*1000) // TODO: check how to make this to not die before test is finished
    streamingContext.stop(false)

  }

}
