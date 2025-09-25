echo off
if ($env:CLASSPATH) {
  $env:CLASSPATH = ".;$env:CLASSPATH"
} else {
  $env:CLASSPATH = ".;"
}
$env:PATH_TO_FX = ".\javafx\javafx-sdk-17.0.16\lib"
cls

echo "==== Running Game Extractor ===="
java --module-path $env:PATH_TO_FX --add-modules javafx.swing,javafx.web -Xmx2048m -jar GameExtractor.jar
pause