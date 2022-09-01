#!/bin/bash

# cli install location
cliHome="/opt/corda/cli"
# cli symlink for script
cliSymlink="/usr/bin/corda-cli.sh"
# temp dir
tempDir="/tmp/corda/cli/"
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
wget "${s3Bucket}/cliInstall.zip" -O "${tempDir}/cliInstall.zip"

if [ "$verify" = true ]; then
  wget "${s3Bucket}/cliInstall..zip.md5" -O "${tempDir}/cliInstall.zip.md5"
  if md5sum -c "${tempDir}/cliInstall.zip.mpd5"; then
    echo "md5 checksum verified!"
  else
    echo "Unable to verify md5 checksum"
    exit 1
  fi
fi

# unzip the archive
cd $tempDir
unzip corda-cli-dist.zip

# install to /opt/corda/cli
cd corda-cli-dist
cp -R . $cliHome

# generate script
echo "java -Dpf4j.pluginsDir=$cliHome/plugins -jar $cliHome/corda-cli.jar $@" > $cliHome/corda-cli.sh

# symlink to /usr/bin
ln -s $cliHome/corda-cli.sh $cliSymlink