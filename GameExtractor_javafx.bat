echo off
SET CLASSPATH=.;%CLASSPATH%
SET PATH_TO_FX=".\javafx\javafx-sdk-17.0.16\lib"
cls

echo ==== Running Game Extractor ====
java --module-path %PATH_TO_FX% --add-modules javafx.swing,javafx.web -Xmx2048m -jar GameExtractor.jar