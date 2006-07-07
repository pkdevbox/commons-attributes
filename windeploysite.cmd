@ECHO OFF

if "%1" == "" goto :HELP

call maven attributes:windeploysite
pscp target/site.zip %1@people.apache.org:__site.zip
plink %1@people.apache.org "rm -rf /www/jakarta.apache.org/commons/attributes/*;unzip __site.zip -d /www/jakarta.apache.org/commons/attributes/;rm -f __site.zip"

goto END

:HELP

echo Usage: windeploysite username
echo Example: windeploysite leosutic
echo This script assumes you have the PuTTY SSH client in your path. 
echo See http://www.chiark.greenend.org.uk/~sgtatham/putty/
echo for instructions on how to download and install PuTTY.

:END