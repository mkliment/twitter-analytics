package com.tweets

import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.twitter.TwitterUtils
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder
import twitter4j.Status

trait TwitterStream {

  def getTwitterStream(spark: SparkSession, streamingContext: StreamingContext, filterTweets: Seq[String]): ReceiverInputDStream[Status] = {

    // TODO: add env.get and pass them during build in CI/CD steps
    val consumerKey = s"wq1rXuWaqQjpVUHuxKqk2dvAg"
    val consumerSecret = s"9JGoms8F6TNGxhkAmmrFWXIBAjaMKIs8gElQAWyYXYSiihFPo4"
    val accessToken = s"24868031-0yv5EkrX3u9DPWbSckkZWJj8KZAPjRf10lAv1iGCz"
    val accessTokenSecret = s"CTmfDiBs4AFwnvq4Sxesl3kOlvwNBw42s3ACnYYlKZZDM"

    System.setProperty("twitter4j.oauth.consumerKey", consumerKey)
    System.setProperty("twitter4j.oauth.consumerSecret", consumerSecret)
    System.setProperty("twitter4j.oauth.accessToken", accessToken)
    System.setProperty("twitter4j.oauth.accessTokenSecret", accessTokenSecret)

    val cb = new ConfigurationBuilder
    cb.setDebugEnabled(true)
      .setOAuthConsumerKey(consumerKey)
      .setOAuthConsumerSecret(consumerSecret)
      .setOAuthAccessToken(accessToken)
      .setOAuthAccessTokenSecret(accessTokenSecret)
    val auth = new OAuthAuthorization(cb.build)

    TwitterUtils.createStream(streamingContext, Some(auth), filterTweets)

  }

}
