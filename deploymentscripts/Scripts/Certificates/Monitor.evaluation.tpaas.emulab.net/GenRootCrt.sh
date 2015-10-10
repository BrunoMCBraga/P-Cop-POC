#!/bin/bash
 
#Required
domain=$1
commonname=$domain
echo "$domain"
 
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
openssl genrsa -out "$domain/$domain.key" 2048
 
#Create the request
echo "Creating Root Certificate"
openssl req -new -x509 -days 365 -key "$domain/$domain.key" -out "$domain/$domain.crt" -subj "/C=$country/ST=$state/L=$locality/O=$organization/OU=$organizationalunit/CN=$commonname/emailAddress=$email"
