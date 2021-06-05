package com.tweets

import scopt.OptionParser

case class UsageConfig(
    topNSamples: Option[String] = Some("10"),
    triggerIntervalSeconds: Option[Long] = Some(10L),
    windowIntervalSeconds: Option[String] = None,
    watermarkIntervalSeconds: Option[String] = None,
    windowSlideDurSeconds: Option[String] = None
)

class UsageOptionParser extends OptionParser[UsageConfig]("job config") {
  head("scopt", "3.x")

  opt[String]("topNSamples") action { (value, config) =>
    config.copy(topNSamples = Some(value))
  } text ("select top N samples of given dataset")

  opt[Long]("triggerIntervalSeconds") action { (value, config) =>
    config.copy(triggerIntervalSeconds = Some(value))
  } text "number of seconds to trigger processing of micro batch in streaming"

  opt[String]("windowIntervalSeconds") action { (value, config) =>
    config.copy(windowIntervalSeconds = Some(value))
  } text "the duration of the window in seconds"

  opt[String]("watermarkIntervalSeconds") action { (value, config) =>
    config.copy(watermarkIntervalSeconds = Some(value))
  } text "a threshold to specify how long in seconds the Spark engine waits for late events in streaming"

  opt[String]("windowSlideDurSeconds") action { (value, config) =>
    config.copy(windowSlideDurSeconds = Some(value))
  } text "the duration of slide interval for the window in seconds"
}
