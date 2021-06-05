package com.tweets.storage

import java.io.{FileWriter, PrintWriter}
import java.nio.file.{Files, Paths}

import org.apache.spark.sql.{DataFrame, Dataset, Encoders, SparkSession}
import org.slf4j.{Logger, LoggerFactory}

trait Storage {

  def writeToCsv(ds: Dataset[_], saveMode: String, location: String)
  def writeStringToFile(outputPath: String, outputFile: String, content: String, append: Boolean = true)
}

class LocalStorage(spark: SparkSession) extends Storage {

  /**
    * Storage defines all input and output logic. How and where tables and files
    * should be read and saved.
    */
  import spark.implicits._
  val LOG: Logger = LoggerFactory.getLogger("Storage")


  override def writeToCsv(ds: Dataset[_], saveMode: String, location: String): Unit = {
    ds.write
      .option("header", "true")
      .mode(saveMode)
      .csv(location)
  }

  override def writeStringToFile(outputPath: String, outputFile: String, content: String, append: Boolean = true): Unit = {
    val fullPath = s"$outputPath/$outputFile"
    val dir = Paths.get(outputPath)

    if (!Files.exists(dir)) {
      Files.createDirectories(dir)
      LOG.info(s"Created missing directory $dir")
    }

    val fileWriter = new FileWriter(fullPath, append)
    val printWriter = new PrintWriter(fileWriter)

    printWriter.println(content)
    fileWriter.close()
  }

}
