# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Minion2.evaluation.tpaas.emulab.net" -in "./Minion2.evaluation.tpaas.emulab.net.crt" -inkey "./Minion2.evaluation.tpaas.emulab.net.key" -out "Minion2.evaluation.tpaas.emulab.net.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./Minion2.evaluation.tpaas.emulab.net.jks" -srckeystore "./Minion2.evaluation.tpaas.emulab.net.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Minion2.evaluation.tpaas.emulab.net" -storepass "Passss" -keypass "Passss" -noprompt

#TrusteStore
keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMonitors.jks" -storepass "Passss" -noprompt

keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedHubs.jks" -storepass "Passss" -noprompt
keytool -import -alias "AHub1.evaluation.tpaas.emulab.net" -file "../AHub1.evaluation.tpaas.emulab.net/AHub1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedHubs.jks" -storepass "Passss" -noprompt
