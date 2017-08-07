@ECHO OFF

cd /d %~dp0

Set StartInDirectory=%CD%


setLocal EnableDelayedExpansion
set CLASSPATH="
for /R ./lib %%a in (*.jar) do (
   set CLASSPATH=!CLASSPATH!;%%a
)
set CLASSPATH=!CLASSPATH!"

if %1.==. (
     echo For detailed information, use help+following class names:
     echo Import
     echo Runpipe
     echo Viewer
     echo RunCPE
     echo XMISQLAgainstGoldComparater
     echo XMISQLSimpleComparator
 ) else (
    ${java.path} -cp ./classes;!CLASSPATH! edu.utah.bmi.runner.%* help
)

