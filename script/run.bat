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
    echo use "run classname parameters" to execute classes.
) else (
    ${java.win.path} -cp ./classes;!CLASSPATH! edu.utah.bmi.runner.%*
)
