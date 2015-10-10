# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Minion1.evaluation.tpaas.emulab.net" -in "./Minion1.evaluation.tpaas.emulab.net.crt" -inkey "./Minion1.evaluation.tpaas.emulab.net.key" -out "Minion1.evaluation.tpaas.emulab.net.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./Minion1.evaluation.tpaas.emulab.net.jks" -srckeystore "./Minion1.evaluation.tpaas.emulab.net.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Minion1.evaluation.tpaas.emulab.net" -storepass "Passss" -keypass "Passss" -noprompt

#TrusteStore
keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMonitors.jks" -storepass "Passss" -keypass "Passss" -noprompt
keytool -import -alias "AHub1.evaluation.tpaas.emulab.net" -file "../AHub1.evaluation.tpaas.emulab.net/AHub1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedHubs.jks" -storepass "Passss" -keypass "Passss" -noprompt
