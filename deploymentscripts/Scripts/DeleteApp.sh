AppId="$1"
AppDir="../../AppsMinion/""$AppId/"
AppContainer=`docker ps -a | grep "$AppId" | awk '{print $1 }'`
AppImage=`docker images -a | grep "$AppId" | awk '{ print $3}'`

sudo docker stop $AppContainer
sudo docker rm $AppContainer
sudo docker rmi -f $AppImage
rm -rf "$AppDir"
exit 0
