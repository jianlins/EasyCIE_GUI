#!/usr/bin/env bash

cd ..
base="$PWD"
if [ ! -d "$base/target/release" ]; then
  mkdir target/release
fi
value=`cat pom.xml`
find_version() {
  local s=$1 regex=$2
  if [[ $s =~ $regex ]]
    then
      echo "${BASH_REMATCH[1]}"
  fi
}
version=$(find_version "$value" "current.version>([^<]+)")
echo $version


cd target/deploy
sed -i 's/jdk1.8\/bin\/java/java/g' run_gui
chmod +x run_gui
sed -i 's/jdk1.8\\bin\\java/java/g' run_gui.bat
chmod +x run_gui.bat
zip -r "../release/EasyCIE_${version}_wo_jdk.zip" "./"
cd $base/target/deploy
sed -i 's/jdk1.8\/bin\/java/java/g' run_gui
sed -i 's/jdk1.8\\bin\\java/java/g' run_gui.bat