sudo yum -y update
sudo yum -y install java-1.8.0-openjdk-src.x86_64 && sudo yum -y install java-1.8.0-openjdk-headless.x86_64  && sudo yum -y install java-1.8.0-openjdk-devel.x86_64

UploadedFilesDir="UploadedFiles/"
SrcDir="$UploadedFilesDir""src/"
BinDir="$UploadedFilesDir""bin/"
CertificatesDir="$UploadedFilesDir""Certificates/"
CreateStoresScript="CreateStores.sh"


#Certficates:
#1.Run createallsertificates.sh
#2.go to each directory and run the createstores.sh

if [ "$1" == "-M" ]
then
	echo "On monitor..."
	mkdir "$BinDir"
else
	sleep 30s	
fi

hostFolder="$2/"
cd "$CertificatesDir$hostFolder"
"./$CreateStoresScript"
cp *.jks "../../bin/"
cd "../../../"

if [ "$1" == "-M" ]
then
	echo "On monitor..."
	javac -d "$BinDir" `find $SrcDir -name "*.java"`
	cd "$BinDir"
	java "monitor/Monitor" -u securepa -k ../ssh_key
	
elif [ "$1" == "-m" ]
then
	
	echo "On minion..."
	curl -sSL "https://get.docker.com/" | sudo sh
	sudo service docker start
	sleep 1m
	cd "$BinDir"
	java "minion/NodeGuard" -m "Monitor.evaluation.tpaas.emulab.net"
	

		
elif [ "$1" == "-h" ]
then
	echo "On hub..."
	hubDomain="$2"
	sleep 1m
	cd "$BinDir"
	#There should be a hub registering.
	java "auditinghub/AuditingHub" -u securepa -k ../ssh_key -m "Monitor.evaluation.tpaas.emulab.net" -h "$hubDomain"	
fi	



