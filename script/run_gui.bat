@ECHO OFF
cd /d %~dp0
Set StartInDirectory=%CD%
setLocal EnableDelayedExpansion
${java.win.path} -jar easycie-gui.jar

