@ECHO OFF

if "%1" == "" goto :HELP

pscp target/site.zip %1@cvs.apache.org:__site.zip
plink %1@cvs.apache.org "rm -rf /www/jakarta.apache.org/commons/attributes/*;unzip __site.zip -d /www/jakarta.apache.org/commons/attributes/;rm -f __site.zip"

goto END

:HELP

echo Usage: windeploysite username
echo Example: windeploysite leosutic

:END