#!/bin/bash

# cli install location
cliHome="/opt/corda/cli"
# cli symlink for script
cliSymlink="/usr/bin/corda-cli.sh"
# temp dir
tempDir=$(mktemp -d)
# base s3 url
s3Bucket=TEMPLATE_URL

# use md5 checksum or not
verify=${verify:-false}

while getopts "v" opt; do
  case $opt in
  v)
    verify=true
    ;;
  esac
done

# download package to tmp and verify if requested
wget "${s3Bucket}.zip" -P "$tempDir"

if [ "$verify" = true ]; then
  wget "${s3Bucket}.zip.md5" -O "${tempDir}.zip.md5"

  str=$(md5sum corda-cli*.zip)
  sum=$(cat corda-cli*.zip.md5)

  if [[ "$str" == *"$sum"* ]];
  then
    echo "Checksums match!"
  else
    echo "Checksums do not match"
    exit 1
  fi
fi

# unzip the archive
cd "$tempDir" || exit
unzip corda-cli-dist.zip

cd corda-cli-dist

usr=$(id -u)
grp=$(id -g)
# generate script
# shellcheck disable=SC2145
echo "java -Dpf4j.pluginsDir=$cliHome/plugins -jar $cliHome/corda-cli.jar $@" > corda-cli.sh
chmod 755 corda-cli.sh

sudo bash -c """
# install to /opt/corda/cli
mkdir -p $cliHome
cp -R . $cliHome

# symlink to /usr/bin
ln -s $cliHome/corda-cli.sh $cliSymlink

chown $usr:$grp -R $cliHome
"""