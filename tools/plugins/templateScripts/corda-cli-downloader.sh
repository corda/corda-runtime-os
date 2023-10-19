#!/bin/bash

# cli install location
cliHome="/opt/corda/cli"
# cli symlink for script
cliSymlink="/usr/local/bin/corda-cli.sh"
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
  *) echo "Unknown option '${opt}' ignored"
    ;;
  esac
done

# download package to tmp and verify if requested
wget "${s3Bucket}.zip" -O "${tempDir}/corda-cli.zip"

if [ "$verify" = true ]; then
  wget "${s3Bucket}.zip.md5" -O "${tempDir}/corda-cli.zip.md5"

  if echo "$(cat ${tempDir}/corda-cli.zip.md5)  ${tempDir}/corda-cli.zip" | md5sum -c &>/dev/null
  then
    echo "Checksums match!"
  else
    echo "Checksums do not match"
    exit 1
  fi
fi

# unzip the archive
cd "$tempDir" || exit
unzip corda-cli.zip

usr=$(id -u)
grp=$(id -g)
# generate script
echo "java -Dpf4j.pluginsDir=$cliHome/plugins -jar $cliHome/corda-cli.jar \"\$@\"" > corda-cli.sh
chmod 755 corda-cli.sh

sudo bash -c """
# install to /opt/corda/cli
mkdir -p $cliHome
cp -R . $cliHome

# symlink to /usr/local/bin
ln -s $cliHome/corda-cli.sh $cliSymlink

chown $usr:$grp -R $cliHome
"""
