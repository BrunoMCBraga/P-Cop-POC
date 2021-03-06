#cafilesDir="cafiles/"
#rm -rf "$cafilesDir"
#mkdir "$cafilesDir"
#touch "$cafilesDir""index.txt"
#echo 1000 > "$cafilesDir""serial"

MonitorDir="Monitor.evaluation.tpaas.emulab.net"
GenCertificateScript="GenDomainCert.sh"
GenRootCrtScript="GenRootCrt.sh"

rm "$MonitorDir/""index.txt"
rm "$MonitorDir/""serial"
touch "$MonitorDir/""index.txt"
echo 1001 > "$MonitorDir/""serial"

"./$MonitorDir/$GenRootCrtScript" "$MonitorDir"

Minion1Dir="Minion1.evaluation.tpaas.emulab.net"
rm "$Minion1Dir/""index.txt"
rm "$Minion1Dir/""serial"
touch "$Minion1Dir/""index.txt"
echo 1002 > "$Minion1Dir/""serial"
"./$GenCertificateScript" "$Minion1Dir" "$MonitorDir"

Minion2Dir="Minion2.evaluation.tpaas.emulab.net"
rm "$Minion2Dir/""index.txt"
rm "$Minion2Dir/""serial"
touch "$Minion2Dir/""index.txt"
echo 1003 > "$Minion2Dir/""serial"
"./$GenCertificateScript" "$Minion2Dir" "$MonitorDir"

Minion3Dir="Minion3.evaluation.tpaas.emulab.net"
rm "$Minion3Dir/""index.txt"
rm "$Minion3Dir/""serial"
touch "$Minion3Dir/""index.txt"
echo 1004 > "$Minion3Dir/""serial"
"./$GenCertificateScript" "$Minion3Dir" "$MonitorDir"

AHub1Dir="AHub1.evaluation.tpaas.emulab.net"
rm "$AHub1Dir/""index.txt"
rm "$AHub1Dir/""serial"
touch "$AHub1Dir/""index.txt"
echo 1005 > "$AHub1Dir/""serial"
"./$GenCertificateScript" "$AHub1Dir" "$MonitorDir"

AdminDir="Admin"
rm "$AdminDir/""index.txt"
rm "$AdminDir/""serial"
touch "$AdminDir/""index.txt"
echo 1006 > "$AdminDir/""serial"
"./$GenCertificateScript" "$AdminDir" "$MonitorDir"

DeveloperDir="Developer"
rm "$DeveloperDir/""index.txt"
rm "$DeveloperDir/""serial"
touch "$DeveloperDir/""index.txt"
echo 1007 > "$DeveloperDir/""serial"
"./$GenCertificateScript" "$DeveloperDir" "$MonitorDir"

AuditorDir="Auditor"
rm "$AuditorDir/""index.txt"
rm "$AuditorDir/""serial"
touch "$AuditorDir/""index.txt"
echo 1008 > "$AuditorDir/""serial"
"./$GenCertificateScript" "$AuditorDir" "$MonitorDir"


