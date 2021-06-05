package com.tweets

import org.slf4j.{Logger, LoggerFactory}

trait Logging {

  @transient protected lazy val LOGGER: Logger =
    LoggerFactory.getLogger(this.getClass)

}
