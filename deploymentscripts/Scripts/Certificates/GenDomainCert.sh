#!/bin/bash
 
#Required
domain=$1
commonname=$domain
signer=$2
 
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
echo "Creating CSR"
openssl req -new -key "$domain/$domain.key" -out "$domain/$domain.csr" -subj "/C=$country/ST=$state/L=$locality/O=$organization/OU=$organizationalunit/CN=$commonname/emailAddress=$email"
 
#Sign request
echo "Signing CSR"
openssl ca -batch -config "openssl.cnf" -cert "$signer/$signer.crt" -keyfile "Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.key" -in "$domain/$domain.csr" -outdir "$domain" -out "$domain/$domain.crt"
