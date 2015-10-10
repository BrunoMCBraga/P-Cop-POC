# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "Monitor.evaluation.tpaas.emulab.net" -in "./Monitor.evaluation.tpaas.emulab.net.crt" -inkey "./Monitor.evaluation.tpaas.emulab.net.key" -out "Monitor.evaluation.tpaas.emulab.net.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./Monitor.evaluation.tpaas.emulab.net.jks" -srckeystore "./Monitor.evaluation.tpaas.emulab.net.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "Monitor.evaluation.tpaas.emulab.net" -storepass "Passss" -noprompt

#TrusteStore
keytool -import -alias "AHub1.evaluation.tpaas.emulab.net" -file "../AHub1.evaluation.tpaas.emulab.net/AHub1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
keytool -import -alias "Minion1.evaluation.tpaas.emulab.net" -file "../Minion1.evaluation.tpaas.emulab.net/Minion1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
keytool -import -alias "Minion2.evaluation.tpaas.emulab.net" -file "../Minion2.evaluation.tpaas.emulab.net/Minion2.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
keytool -import -alias "Minion3.evaluation.tpaas.emulab.net" -file "../Minion3.evaluation.tpaas.emulab.net/Minion3.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
