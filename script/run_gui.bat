@ECHO OFF

cd /d %~dp0

Set StartInDirectory=%CD%


setLocal EnableDelayedExpansion
set CLASSPATH="
for /R ./lib %%a in (*.jar) do (
   set CLASSPATH=!CLASSPATH!;%%a
)
set CLASSPATH=!CLASSPATH!"


${java.win.path} -cp ./classes;!CLASSPATH! edu.utah.bmi.simple.gui.controller.Main
