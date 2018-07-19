@ECHO OFF
cd /d %~dp0
Set StartInDirectory=%CD%
setLocal EnableDelayedExpansion
${java.win.path} -cp "lib/*;./classes" edu.utah.bmi.simple.gui.controller.Main

