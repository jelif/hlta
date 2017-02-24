package tm.hlta

import collection.JavaConversions._
import org.latlab.util.Variable
import tm.util.ArffWriter
import java.text.DecimalFormat
import tm.util.Data
import org.latlab.model.LTM
import java.nio.file.Files
import java.nio.file.Paths
import java.io.PrintWriter

/**
 * Assign topics to the documents.  It computes the probabilities of the latent
 * variables in a LTM and assign those topics that higher probability of state 1
 * to each document.
 */
object AssignTopics {
  def main(args: Array[String]) {
    if (args.length < 3)
      printUsage()
    else
      run(args(0), args(1), args(2))
  }

  def printUsage() = {
    println("AssignTopics model_file data_file outputName")
    println
    println("E.g. AssignTopics model.bif data.arff output")
    println("The output file will be output.topics.js and output.topics.arff")
  }

  def run(modelFile: String, dataFile: String, outputName: String): Unit = {
    val topicDataFile = outputName + ".topics.arff"
    val (model, topicData) = if (Files.exists(Paths.get(topicDataFile))) {
      println(s"Topic data file (${topicDataFile}) exists.  Skipped computing topic data.")
      Reader.readLTMAndARFFData(modelFile, topicDataFile)
    } else {
      val (model, topicData) = readModelAndComputeTopicData(modelFile, dataFile)

      println("Saving topic data")
      outputName + "-topics"

      topicData.saveAsArff(outputName + "-topics",
        topicDataFile, new DecimalFormat("#0.##"))
      (model, topicData)
    }

    println("Generating topic map")
    val map = generateTopicToDocumentMap(topicData, 0.5)

    println("Saving topic map")
    writeTopicMap(map, outputName + ".topics.json")
  }

  def readModelAndComputeTopicData(modelFile: String, dataFile: String) = {
    println("reading model and data")
    val (model, data) = Reader.readLTMAndARFFData(modelFile, dataFile)
    val variableNames = data.variables.map(_.getName)

    println("binarizing data")
    val binaryData = data.binary
    model.synchronize(binaryData.variables.toArray)

    val variables = model.getInternalVars.toIndexedSeq

    println("Computing topic distribution")
    // find the probabilities of state 1 for each variable
    val topicProbabilities =
      HLTA.computeProbabilities(model, binaryData, variables)
        .map(p => Data.Instance(p._1.toArray.map(_(1)), p._2))

    (model, Data(variables, topicProbabilities.toIndexedSeq))
  }

  /**
   * Generates a list of documents for each topic.
   *
   * Each map value is a sequence of pairs where first element indicates
   * the probability and second element the document index.
   * The sequence is sorted in descending order of probability.
   */
  def generateTopicToDocumentMap(data: Data, threshold: Double) = {
    (0 until data.variables.size).map { v =>
      val documents = data.instances.view.map(_.values(v)) // map to value of v
        .zipWithIndex
        .filter(_._1 >= threshold).force // keep only those values >= threshold
        .sortBy(-_._1) // sort by descending values
      (data.variables(v), documents)
    }.toMap
  }

  def writeTopicMap(map: Map[Variable, Seq[(Double, Int)]], outputFile: String) = {
    val writer = new PrintWriter(outputFile)

    writer.println("[")

    writer.println(map.map { p =>
      val variable = p._1
      val documents = p._2.map(p => "{\"paper\":"+s"${p._2}"+",\"prob\":"+f"${p._1}%.2f}").mkString(",")
      "{\"topic\":\""+variable.getName+"\",\"doc\":["+documents+"]}"
    }.mkString(",\n"))

    writer.println("]")

    writer.close
  }
}