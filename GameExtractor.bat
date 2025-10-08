echo off
SET CLASSPATH=.;%CLASSPATH%
SET PATH=".\jre\bin;%PATH%"
SET JAVA_HOME=".\jre"
cls

echo ==== Running Game Extractor ====
.\jre\bin\java -Xmx1536m -jar GameExtractor.jar
