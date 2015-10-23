# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Auditor" -in "./Auditor.crt" -inkey "./Auditor.key" -out "Auditor.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./Auditor.jks" -srckeystore "./Auditor.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Auditor" -storepass "Passss" -keypass "Passss" -noprompt

#TrusteStore
keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMonitors.jks" -storepass "Passss"  -noprompt

keytool -import -alias "AHub1.evaluation.tpaas.emulab.net" -file "../AHub1.evaluation.tpaas.emulab.net/AHub1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedHubs.jks" -storepass "Passss" -noprompt
