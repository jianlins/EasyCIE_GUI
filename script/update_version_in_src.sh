#!/usr/bin/env bash
find_version() {
  local s=$1 regex=$2
  if [[ $s =~ $regex ]]
    then
      echo "current version in java code: ${BASH_REMATCH[1]}"
  fi
}

if [ "$2" != "" ]; then
 value=`cat $1`
 find_version "$value" "\(version ([^\)]+)\)"
 sed -r -i -e 's/\(version [^\)]+\)/(version $2)/g' $1
 echo "update to $2"
elif [ "$1" != "" ]; then
 value=`cat src/main/java/edu/utah/bmi/simple/gui/controller/RootLayoutController.java`
 find_version "$value" "\(version ([^\)]+)\)"
 sed -r -i -e "s/\(version [^\)]+\)/(version $1)/g" src/main/java/edu/utah/bmi/simple/gui/controller/RootLayoutController.java
 echo "update to $1"
else
 echo ""
fi