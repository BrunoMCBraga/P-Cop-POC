#!/bin/bash
 
#Required
domain=$1
commonname=$domain
 
#Change to your company details
country="PT"
state="TrustedLand"
locality="TrustedCity"
organization="TrustedCloud"
organizationalunit="P-Cop"
email="admin@trustedcloud.net"
 
if [ -z "$domain" ]
then
    echo "Argument not present."
    echo "Useage $0 [common name]"
 
    exit 99
fi

 
echo "Generating key request for $commonname"
 
#Generate a key
openssl genrsa -out $domain.key 2048 -noout
 
#Create the request
echo "Creating CSR"
openssl req -new -key $domain.key -out $domain.csr -subj "/C=$country/ST=$state/L=$locality/O=$organization/OU=$organizationalunit/CN=$commonname/emailAddress=$email"
 
echo "---------------------------"
echo "-----Below is your CSR-----"
echo "---------------------------"
echo
cat $domain.csr
 
echo
echo "---------------------------"
echo "-----Below is your Key-----"
echo "---------------------------"
echo
cat $domain.key
