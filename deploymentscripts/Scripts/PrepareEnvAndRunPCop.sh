sudo yum -y update
sudo yum -y install java-1.8.0-openjdk-src.x86_64 && sudo yum -y install java-1.8.0-openjdk-headless.x86_64  && sudo yum -y install java-1.8.0-openjdk-devel.x86_64
sudo yum -y install tpm-tools trousers jsvc

UploadedFilesDir="UploadedFiles/"
JTSS_FILES_DIR="$UploadedFilesDir""jTSS_0.7.1a/"
SrcDir="$UploadedFilesDir""src/"
BinDir="$UploadedFilesDir""bin/"
CertificatesDir="$UploadedFilesDir""Certificates/"
CreateStoresScript="CreateStores.sh"


sudo modprobe tpm
sudo modprobe tpm_bios tpm_infineon tpm_nsc tpm_tis
#service tcsd start

#Certficates:
#1.Run createallsertificates.sh
#2.go to each directory and run the createstores.sh

hostFolder=""
#cd "$JTSS_FILES_DIR""soap"
cd "$JTSS_FILES_DIR""soap"
sudo bash -c "export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk && ./tcs_daemon.sh start"
cd
if [ "$1" == "-M" ]
then
	echo "On monitor..."
	mkdir "$BinDir"
	hostFolder="$2/"
else
	sleep 30s
	hostFolder="$4/"	
fi

cd "$CertificatesDir$hostFolder"
"./$CreateStoresScript"
cp *.jks "../../bin/"
cd "../../../"

if [ "$1" == "-M" ]
then
	echo "On monitor..."
	javac -d "$BinDir" `find $SrcDir -name "*.java"`
	cd "$BinDir"
	java "monitor/Monitor" -u securepa -k ../ssh_key -h "$2"
	
elif [ "$1" == "-m" ]
then
	
	echo "On minion..."
	curl -sSL "https://get.docker.com/" | sudo sh
	sudo service docker start
	sleep 1m
	cd "$BinDir"
	java "minion/NodeGuard" -m "$2" -h "$4"
	

		
elif [ "$1" == "-a" ]
then
	echo "On hub..."
	sleep 1m
	cd "$BinDir"
	#There should be a hub registering.
	java "auditinghub/AuditingHub" -u securepa -k ../ssh_key -m "$2" -h "$4"	
fi	



