#!/bin/csh

if (! ${?CLASSPATH} ) then 
  setenv CLASSPATH "."
else 
  setenv CLASSPATH ".;$CLASSPATH"
endif

setenv PATH_TO_FX "./javafx/javafx-sdk-17.0.16/lib"

echo "==== Running Game Extractor ===="
java --module-path %PATH_TO_FX% --add-modules javafx.swing,javafx.web -Xmx2048m -jar GameExtractor.jar
