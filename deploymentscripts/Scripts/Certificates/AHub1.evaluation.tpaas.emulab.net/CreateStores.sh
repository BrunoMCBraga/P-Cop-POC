# Create PKCS12 keystore from private key and public certificate.
openssl pkcs12 -export -name "AHub1.evaluation.tpaas.emulab.net" -in "./AHub1.evaluation.tpaas.emulab.net.crt" -inkey "./AHub1.evaluation.tpaas.emulab.net.key" -out "AHub1.evaluation.tpaas.emulab.net.p12" -password "pass:Passss"

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore "./AHub1.evaluation.tpaas.emulab.net.jks" -srckeystore "./AHub1.evaluation.tpaas.emulab.net.p12" -srcstorepass "Passss" -srcstoretype "pkcs12" -alias "AHub1.evaluation.tpaas.emulab.net" -storepass "Passss" -keypass "Passss" -noprompt


#TrusteStore
#keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedAdmins.jks" -storepass "Passss" -noprompt
#keytool -import -alias "Admin" -file "../Admin/Admin.crt" -keystore "TrustedAdmins.jks" -storepass "Passss" -noprompt

keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMonitors.jks" -storepass "Passss" -noprompt

keytool -import -alias "Monitor.evaluation.tpaas.emulab.net" -file "../Monitor.evaluation.tpaas.emulab.net/Monitor.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
keytool -import -alias "Minion1.evaluation.tpaas.emulab.net" -file "../Minion1.evaluation.tpaas.emulab.net/Minion1.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss"  -noprompt
keytool -import -alias "Minion2.evaluation.tpaas.emulab.net" -file "../Minion2.evaluation.tpaas.emulab.net/Minion2.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss"  -noprompt
keytool -import -alias "Minion3.evaluation.tpaas.emulab.net" -file "../Minion3.evaluation.tpaas.emulab.net/Minion3.evaluation.tpaas.emulab.net.crt" -keystore "TrustedMinions.jks" -storepass "Passss" -noprompt
