sudo yum -y update
sudo yum -y install java-1.8.0-openjdk-src.x86_64 && sudo yum -y install java-1.8.0-openjdk-headless.x86_64  && sudo yum -y install java-1.8.0-openjdk-devel.x86_64

UploadedFilesDir="UploadedFiles/"
SrcDir="$UploadedFilesDir""src/"
BinDir="$UploadedFilesDir""bin/"
CertificatesDir=""$UploadedFilesDir""Certificates/""
GenCertificateRequestScript="GenRequest.sh"
SignCertificateRequestScript="SignCertificate.sh"

if [ "$1" == "-M" ]
then
	echo "On monitor..."
	mkdir "$BinDir"
	javac -d "$BinDir" `find $SrcDir -name "*.java"`
	monitorDomain="$2"
	cd "$CertificatesDir""$monitorDomain/"
	#"./$GenCertificateRequestScript" "$monitorDomain"
	#"./$SignCertificateRequestScript" "./" "$monitorDomain" "$monitorDomain"
	keytool -import -alias "MonitorCert" -file "./$monitorDomain".crt -keystore "Monitor" -storepass "Pass" -noprompt
	cd "../../../"
	ls
	cd "$BinDir"
	java "monitor/Monitor" -u securepa -k ../ssh_key
	
elif [ "$1" == "-m" ]
then
	
	echo "On minion..."
	curl -sSL "https://get.docker.com/" | sudo sh
	sudo service docker start
	minionDomain="$2"
	cd "$CertificatesDir""$minionDomain/"
	keytool -import -alias "MinionCert" -file "./$minionDomain".crt -keystore "Minion" -storepass "Pass" -noprompt
	cd "../../../"
	sleep 1m
	cd "$BinDir"
	declare -a argsArray=("$@")
	for ((i=2; i<$#; i++)); do
   		java "minion/NodeGuard" -m "${argsArray[$i]}"
	done

		
elif [ "$1" == "-h" ]
then
	echo "On hub..."
	hubDomain="$2"
	cd "$CertificatesDir""$hubDomain/"
	keytool -import -alias "AHubCert" -file "./$hubDomain".crt -keystore "AHub" -storepass "Pass" -noprompt
	cd "../../../"
	cd "$BinDir"
	#There should be a hub registering.
	java "auditinghub/AuditingHub" -u securepa -k ../ssh_key -m "monitor.evaluation.tpaas.emulab.net"
	sleep 1m
fi	



