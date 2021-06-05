package com.tweets.storage

trait Schema

case class TweetHashtagsSchema(text: String, hashtags: Array[String], createdAt: String) extends Schema

case class TweetCountSchema(hourTs: String, hashtag: String, countSmiles: Long) extends Schema

case class TweetTextCountSchema(text: String, hourTs: String, hashtags: Array[String], countSmiles: Long) extends Schema
