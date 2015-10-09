MonitorDir="Monitor.evaluation.tpaas.emulab.net"
RequestScript="GenRequest.sh"
SignScript="SignCertificate.sh"

cd "$MonitorDir"
ls
"./$RequestScript" "$MonitorDir"
"./$SignScript" "." "$MonitorDir" "$MonitorDir"
cd ..

Minion1Dir="Minion1.evaluation.tpaas.emulab.net"

cd "$Minion1Dir"
"./$RequestScript" "$Minion1Dir"
cd "../$MonitorDir"
"./$SignScript" "../$Minion1Dir" "$Minion1Dir" "$MonitorDir"
cd ..

Minion2Dir="Minion2.evaluation.tpaas.emulab.net"

cd "$Minion2Dir"
"./$RequestScript" "$Minion2Dir"
cd "../$MonitorDir"
"./$SignScript" "../$Minion2Dir" "$Minion2Dir" "$MonitorDir"
cd ..

Minion3Dir="Minion3.evaluation.tpaas.emulab.net"

cd "$Minion3Dir"
"./$RequestScript" "$Minion3Dir"
cd "../$MonitorDir"
"./$SignScript" "../$Minion3Dir" "$Minion3Dir" "$MonitorDir"
cd ..

AHub1Dir="AHub1.evaluation.tpaas.emulab.net"

cd "$AHub1Dir"
"./$RequestScript" "$AHub1Dir"
cd "../$MonitorDir"
"./$SignScript" "../$AHub1Dir" "$AHub1Dir" "$MonitorDir"
cd ..


AdminDir="Admin"

cd "$AdminDir"
"./$RequestScript" "$AdminDir"
cd "../$MonitorDir"
"./$SignScript" "../$AdminDir" "$AdminDir" "$MonitorDir"
cd ..


DeveloperDir="Developer"

cd "$DeveloperDir"
"./$RequestScript" "$DeveloperDir"
cd "../$MonitorDir"
"./$SignScript" "../$DeveloperDir" "$DeveloperDir" "$MonitorDir"
cd ..



