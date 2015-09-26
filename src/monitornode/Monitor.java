package monitornode;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import global.Ports;

/*
 * Must expect connections from developers
 * Must expect connections from logger
 * Must expect connections from nodes
 * 
 * Must have users list, nodes list and distinguish between trusted and untrusted
 * */

public class Monitor {

	//Maps IPs to Minions
	private Map<String,Minion> trustedMinions;
	private Object[] trustedMinionsArray;
	private Map<String,Minion> untrustedMinions;
	private Random trustedMinionsGenerator;


	public Monitor(){

		this.trustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsArray = this.trustedMinions.entrySet().toArray();
		this.untrustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsGenerator = new Random();
	}

	public void addNewMinion(Minion newMinion){

		trustedMinions.put(newMinion.getIpAddress(), newMinion);
		this.trustedMinionsArray = trustedMinions.entrySet().toArray();
		//check the boolean??

	}

	public void removeMinion(Minion minion){

		
		if(trustedMinions.remove(minion.getIpAddress()) == null){
			untrustedMinions.remove(minion.getIpAddress());
		}
		else
			this.trustedMinionsArray = trustedMinions.entrySet().toArray();

			
		//check the boolean??

	}
	
	public void setMinionUntrusted(String ipAddress){
		Minion trustedMinion = trustedMinions.remove(ipAddress);
		if (trustedMinion != null)
			untrustedMinions.put(ipAddress, trustedMinion);
		this.trustedMinionsArray = trustedMinions.entrySet().toArray();

		
	}
	
	public void setMinionTrusted(String ipAddress){
		Minion untrustedMinion = untrustedMinions.remove(ipAddress);
		if (untrustedMinion != null)
			trustedMinions.put(ipAddress, untrustedMinion);
		this.trustedMinionsArray = trustedMinions.entrySet().toArray();

		
	}
	//Should be able to pick more than one
	public Minion pickTrustedMinion(){
		int trustedIndex = trustedMinionsGenerator.nextInt(trustedMinions.size()-1);
		return ((Entry<String, Minion>)trustedMinionsArray[trustedIndex]).getValue();
		
	}

	public static void main(String[] args){

		Monitor monitor = new Monitor();
		ServerSocket developersServerSocket = null;
		ServerSocket minionsServerSocket = null;
		ServerSocket hubsServerSocket = null;
		try {
			developersServerSocket = new ServerSocket(Ports.MONITOR_DEVELOPER_PORT);
			minionsServerSocket = new ServerSocket(Ports.MONITOR_MINION_PORT);
			hubsServerSocket = new ServerSocket(Ports.MONITOR_HUB_PORT);
		} catch (IOException e) {
			System.err.println("Error starting server sockets:"+ e.getMessage());
			System.exit(1);
		}

		new Thread(new HubsRequestsHandler(hubsServerSocket,monitor)).start();
		new Thread(new DevelopersRequestsHandler(developersServerSocket,monitor)).start();
		new Thread(new MinionsRequestsHandler(minionsServerSocket,monitor)).start();

	}
}
