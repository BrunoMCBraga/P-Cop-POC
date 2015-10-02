#Usage: swapandclean projectid experimentid sshkeyfile username

ProjectID="$1"
ExperimentID="$2"
AuthKey="$3"
Username="$4"
CommandHost="users.emulab.net"

echo "$ProjectID"

echo "Swapping in..."
ReloadCommand="swapexp -w -e $ProjectID,$ExperimentID in"
ssh -t -i $AuthKey $Username"@"$CommandHost $ReloadCommand


echo "Cleaning all nodes.Please wait..."
ReloadCommand="os_load -c -e $ProjectID,$ExperimentID"
ssh -t -i $AuthKey $Username"@"$CommandHost $ReloadCommand
echo "Done!"
