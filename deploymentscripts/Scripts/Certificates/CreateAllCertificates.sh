cafilesDir="cafiles/"
rm -rf "$cafilesDir"
mkdir "$cafilesDir"
touch "$cafilesDir""index.txt"
echo 1000 > "$cafilesDir""serial"

MonitorDir="Monitor.evaluation.tpaas.emulab.net"
GenCertificateScript="GenDomainCert.sh"
GenRootCrtScript="GenRootCrt.sh"

"./$MonitorDir/$GenRootCrtScript" "$MonitorDir"

Minion1Dir="Minion1.evaluation.tpaas.emulab.net"
"./$GenCertificateScript" "$Minion1Dir" "$MonitorDir"

Minion2Dir="Minion2.evaluation.tpaas.emulab.net"
"./$GenCertificateScript" "$Minion2Dir" "$MonitorDir"

Minion3Dir="Minion3.evaluation.tpaas.emulab.net"
"./$GenCertificateScript" "$Minion3Dir" "$MonitorDir"

AHub1Dir="AHub1.evaluation.tpaas.emulab.net"
"./$GenCertificateScript" "$AHub1Dir" "$MonitorDir"

AdminDir="Admin"
"./$GenCertificateScript" "$AdminDir" "$MonitorDir"

DeveloperDir="Developer"
"./$GenCertificateScript" "$DeveloperDir" "$MonitorDir"

AuditorDir="Auditor"
"./$GenCertificateScript" "$AuditorDir" "$MonitorDir"


