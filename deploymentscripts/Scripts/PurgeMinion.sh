AppsDir="../../AppsMinion/*"
ContainerIds=`sudo docker ps -a -q | tr '\n' ' '`
ImageIds=`sudo docker images -a -q | tr '\n' ' '`
sudo docker stop $ContainerIds
sudo docker rm $ContainerIds
sudo docker rmi -f $ImageIds
rm -rf "$AppsDir"
exit 0
