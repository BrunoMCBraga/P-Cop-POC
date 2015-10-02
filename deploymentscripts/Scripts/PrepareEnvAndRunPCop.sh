sudo yum -y update
sudo yum -y install java-1.8.0-openjdk-src.x86_64 && sudo yum -y install java-1.8.0-openjdk-headless.x86_64  && sudo yum -y install java-1.8.0-openjdk-devel.x86_64

UploadedFilesDir="UploadedFiles/"
SrcDir="$UploadedFilesDir""src/"
BinDir="$UploadedFilesDir""bin/"

if [ "$1" == "-M" ]
then
	echo "On monitor..."
	mkdir "$BinDir"
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
	declare -a argsArray=("$@")
	for ((i=1; i<$#; i++)); do
   		java "minion/NodeGuard" -m "${argsArray[$i]}"
	done

		
elif [ "$1" == "-h" ]
then
	echo "On hub..."
	cd "$BinDir"
	#There should be a hub registering.
	java "auditinghub/AuditingHub" -u securepa -k ../ssh_key -m "monitor.evaluation.tpaas.emulab.net"
	sleep 1m
fi	



