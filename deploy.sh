#!/bin/bash
set -e

yellow() { echo -e "\033[33m$1\033[0m"; }
green() { echo -e "\033[32m$1\033[0m"; }
red() { echo -e "\033[31m$1\033[0m"; }
bold() { echo -e "\033[1;37m$1\033[0m"; }
_fancymessage() {
  echo ""
  green "\033[32m--> $1 \033[0m"
}

info() { bold "$1"; }
ask() { _fancymessage "$1"; }
fail() { red "$1"; exit 1; }

# read -i funker ikke på OSX - derav mer kronglete løsning :(
_readWithDefault() {
    local default=$1
    read answer
    if [ "$answer" = "" ]; then
         answer="$default"
    fi
    echo $answer
}

info "BYGGER"
./sbt ems-dist/dist

DEFAULT_ZIP=`find . -name ems*.zip`
ask "Hvor ligger zip-filen? [$DEFAULT_ZIP]"
ZIP=$(_readWithDefault $DEFAULT_ZIP)

ask "Til test eller prod? [test]"
ENV=$(_readWithDefault "test")

ask "Er? [test]"
ENV=$(_readWithDefault "test")

if [ ! -f $ZIP ]; then
    fail "Fant ikke $ZIP :("
fi

if [ $ENV != "test" -a $ENV != "prod" ]; then
    fail "Miljø må være enten 'test' eller 'prod'"
fi

if [ $ENV == "prod" ]; then
    HOST="212.71.237.26"
elif [ $ENV == "test" ]; then
    HOST="212.71.238.251"
else
    fail "Det du sa gav null mening!"
fi

info "Deployer til $ENV på $HOST:$BASE med zip $ZIP"
scp $ZIP javabin@$HOST:/home/javabin/web/ems

info "Oppdaterer og restarter EMS"
ssh javabin@$HOST "cd /home/javabin/web/ems/ems && app upgrade && app restart"
