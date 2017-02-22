import AssemblyKeys._ // put this at the top of the file

name := "extract-product-preprocessing"

version := "1"

scalaVersion := "2.11.2"

libraryDependencies += "edu.stanford.nlp" % "stanford-corenlp" % "3.6.0"

assemblySettings

mainClass in assembly := Some("edu.berkeley.forum.main.SystemRunner")

