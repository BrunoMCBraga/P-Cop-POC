#deployall username keyfilepath hostsfilepath


#Credentials for ssh. Let us assume one identity for every node.
Username="$1"
IdentityKey="$2"


#File containing list of monitors, minions and Hubs
#e.g. [Monitors]
#     monitor1.amazon.com
HostsFile="$3"
HostsFileContent=`cat "$HostsFile"`


#AppsDir
AppsMonitorDir="AppsMonitor"
AppsMinionDir="AppsMinion"

#Base init comand
InitCommand="sudo rm -rf ./* && mkdir $AppsMonitorDir $AppsMinionDir"

#Files to be uploaded are here. This includes scripts to be ran and source code.
UploadedFilesPath="UploadedFiles/"
PrepareEnvAndRunPCopScript="PrepareEnvAndRunPCop.sh"
PurgeMinionScript="PurgeMinion.sh"

#Source code path
Src="../src/"
rm -rf "$UploadedFilesPath$Src"
cp -R "$Src" "$UploadedFilesPath"


#Certificates
CertificatesDir="$UploadedFilesPath""Certificates/"
CaFilesDir="$CertificatesDir""cafiles/"
CreateAllCertsScript="CreateAllCertificates.sh"
CreateLocalStoresScript="CreateStores.sh"

rm -rf "$CaFilesDir"
mkdir "$CaFilesDir"
touch "$CaFilesDir""index.txt"
echo 1000 > "$CaFilesDir""serial"
cd "$CertificatesDir"
echo "Creating certificates."
"./$CreateAllCertsScript"

#Create local admin key stores
cd "Admin"
rm -rf *.jks
"./$CreateLocalStoresScript"
cd ..

#Create local developer key stores
cd "Developer"
rm -rf *.jks
"./$CreateLocalStoresScript"

cd ../../../
echo "Certificates created."

#Parse hosts file
MonitorHosts=`echo -n "$HostsFileContent" | tr '\n' ' '| grep -Pzo "(?<=\[Monitors\]).+(?=\[Minions\])"`
MinionHosts=`echo -n "$HostsFileContent" | tr '\n' ' '| grep -Pzo "(?<=\[Minions\]).+(?=\[Hubs\])"`
HubHosts=`echo -n "$HostsFileContent" | tr '\n' ' '| grep -Pzo "(?<=\[Hubs\]).+"`


echo $MonitorHosts
read -d '' -a MonitorHostsArray <<< "$MonitorHosts"
read -d '' -a MinionHostsArray <<< "$MinionHosts"
read -d '' -a HubHostsArray <<< "$HubHosts"


echo "Preparing nodes and uploading scripts"
#since this script uses planetlab nodes, storage is shared on home folders. On rm suffices on one of the nodes.
ssh -t -i "$IdentityKey" "$Username@${MonitorHostsArray[0]}" "$InitCommand"
scp -i "$IdentityKey" -rp "$UploadedFilesPath" "$Username""@""${MonitorHostsArray[0]}:$UploadedFilesPath"

echo "Preparing base environment and running P-Cop on nodes."

for monitor in ${MonitorHostsArray[@]}
do      
        echo "On monitor:$monitor"
        gnome-terminal --title=$monitor -x sh -c 'ssh -t -i '"$IdentityKey $Username@$monitor ""$UploadedFilesPath$PrepareEnvAndRunPCopScript -M $monitor"
        echo "Done!"
done

for minion in ${MinionHostsArray[@]}
do      
        echo "On minion:$minion"
	ssh -t -i "$IdentityKey" "$Username@$minion" "sudo $UploadedFilesPath$PurgeMinionScript"
        gnome-terminal --title=$minion -x sh -c 'ssh -t -i '"$IdentityKey $Username@$minion ""$UploadedFilesPath$PrepareEnvAndRunPCopScript -m $minion"
        echo "Done!"
done

for hub in ${HubHostsArray[@]}
do      
        echo "On minion:$hub"
        gnome-terminal --title=$hub -x sh -c 'ssh -t -i '"$IdentityKey $Username@$hub ""$UploadedFilesPath$PrepareEnvAndRunPCopScript -h $hub"
        echo "Done!"
done

