package rozwiazanie

import java.io.File
import java.net.URL

import com.typesafe.config.ConfigFactory
import org.apache.spark.sql.{DataFrame, SparkSession}

import scala.sys.process._
import scala.util._

object zamowieniaPodsumowanie{

  val sparkMaster = ConfigFactory.load.getString("spark.master")

  val spark = SparkSession
    .builder()
    .master(sparkMaster)
    .appName("Zadanie stazowe - Allegro 2018")
    .getOrCreate()


  def fileDownloader(url: String, filename: String) = {
    print("Downloading file " + filename + "...")
    new URL(url) #> new File("src/main/resources/"+filename) !!

    println("done")
  }

  def downloadFiles(noOfFiles: Int): Unit = {
    if(noOfFiles > 0) {
      val fileName = "TED_CN_"+(2006+noOfFiles-1).toString + ".csv"
      if((new java.io.File("src/main/resources/"+fileName).exists)) {
        println("File " + fileName + " already exists")
      }
      else {
        val url = "http://data.europa.eu/euodp/repository/ec/dg-grow/mapps/TED_CN_"+(2007+noOfFiles-1).toString + ".csv"
        fileDownloader(url, "TED_CN_"+(2006+noOfFiles-1).toString + ".csv")
      }
      downloadFiles(noOfFiles-1)
    }
  }

  def getDataFromFile(path: String) : Try[DataFrame] = {
    try {
      val df : DataFrame = spark
        .read
        .option("header", "true")
        .csv(path)
      Success(df)
    } catch {
      case ex: org.apache.spark.sql.AnalysisException => {
        println(s"""File $path not found""")
        Failure(ex)
      }
      case unknown: Exception => {
        println(s"""Unknown exception: $unknown""")
        Failure(unknown)
      }
    }
  }

  def main(args: Array[String]): Unit = {
    if(args.length > 0) {
      println("The number of files that will be used to get data: " + args(0))
      downloadFiles(args(0).toInt)
    }
    else {
      println("The number of files that will be used to get data: 1")
      downloadFiles(1)
    }
    getDataFromFile("src/main/resources/countries_code.csv") match {
      case Success(codesDf) => {
        codesDf.createOrReplaceTempView("CODES")

        getDataFromFile("src/main/resources/TED_CN_*") match {
          case Success(contractNotices) => {
            contractNotices
              .select("ISO_COUNTRY_CODE", "VALUE_EURO_FIN_2")
              .createOrReplaceTempView("CA")

            val filteredData = spark.sql(
              "SELECT * " +
                "FROM CA " +
                "JOIN CODES ON CA.ISO_COUNTRY_CODE = CODES.CODE " +
                "WHERE CA.VALUE_EURO_FIN_2 IS NOT NULL")
              .createOrReplaceTempView("FILTERED_DATA")

            val result = spark.sql(
              "SELECT COUNTRY, COUNT(*) AS COUNT, ROUND(AVG(VALUE_EURO_FIN_2), 2) AS AVERAGE_VALUE " +
                "FROM FILTERED_DATA " +
                "GROUP BY COUNTRY " +
                "ORDER BY COUNT DESC").show(100)
          }
          case Failure(ex) => {
            println("Exception while geting data...closing programm...")
          }
        }
      }
      case Failure(ex) => {
        println("File with country codes not found...closing programm...")
      }
    }

    spark.close()
  }

}
