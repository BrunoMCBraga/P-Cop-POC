#Keystore

# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Admin" -in "Admin.crt" -inkey "Admin.key" -out "AdminStore.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "AdminStore.jks" -srckeystore "AdminStore.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Admin" -storepass "Passss" -noprompt

#TrusteStore
keytool -import -alias "AHub1.evaluation.tpaas.emulab.net" -file "../AHub1.evaluation.tpaas.emulab.net/AHub1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedHubs.jks" -storepass "Passss" -noprompt
