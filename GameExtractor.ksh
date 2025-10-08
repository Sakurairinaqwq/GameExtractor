#!/bin/ksh
export CLASSPATH=".;$CLASSPATH"
echo "==== Running Game Extractor ===="
java -Xmx1536m -jar GameExtractor.jar
