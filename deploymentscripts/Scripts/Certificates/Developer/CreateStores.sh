# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Developer" -in "./Developer.crt" -inkey "./Developer.key" -out "Developer.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./Developer.jks" -srckeystore "./Developer.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Developer" -storepass "Passss" -noprompt

#TrusteStore
keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMonitors.jks" -storepass "Passss" -noprompt

