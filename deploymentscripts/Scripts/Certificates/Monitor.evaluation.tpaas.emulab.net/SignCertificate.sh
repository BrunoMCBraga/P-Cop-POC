#!/bin/bash
 
#Required
requestPath=$1
signedDomain=$2
monitorDomain=$3

openssl x509 -req -days 365 -in "$requestPath/"$signedDomain.csr -signkey $monitorDomain.key -out "$requestPath/"$signedDomain.crt
 
echo "---------------------------"
echo "-----Below is your CRT-----"
echo "---------------------------"
echo
cat "$requestPath/"$signedDomain.crt
 
