package monitor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import exceptions.InsufficientMinions;

import java.util.Random;
import java.util.Set;

import global.Ports;

/*
 * Usage Monitor -u username -k sshKey
 * 
 * Must expect connections from developers
 * Must expect connections from logger
 * Must expect connections from nodes
 * 
 * Must have users list, nodes list and distinguish between trusted and untrusted
 * */

public class Monitor {


	private static final int USERNAME_FLAG_INDEX=0;
	private static final int KEY_FLAG_INDEX=2;

	//Maps IPs to Minions
	private Map<String,Minion> trustedMinions;
	private Object[] trustedMinionsArray;
	private Map<String,Minion> untrustedMinions;
	private Random trustedMinionsGenerator;
	private Map<String,Minion> appsHosts;
	private Map<String,Application> applications;

	public Monitor(){

		this.trustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsArray = this.trustedMinions.entrySet().toArray();
		this.untrustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsGenerator = new Random();
		this.appsHosts = new Hashtable<String,Minion>();
		this.applications = new Hashtable<String,Application>();

	}

	public void addNewMinion(Minion newMinion){

		trustedMinions.put(newMinion.getIpAddress(), newMinion);
		this.trustedMinionsArray = trustedMinions.entrySet().toArray();
		System.out.println("Added:" + newMinion.getIpAddress() + "Size:" + trustedMinions.size());
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
	public Minion pickTrustedMinion() throws InsufficientMinions{
		if(trustedMinions.size() == 0)
			throw new InsufficientMinions("Expected minions:1 Available:0"); 
		System.out.println("For random, trustedMinions size is:" + trustedMinions.size());
		int trustedIndex = trustedMinionsGenerator.nextInt(trustedMinions.size());
		return ((Entry<String, Minion>)trustedMinionsArray[trustedIndex]).getValue();

	}

	public List<Minion> pickNTrustedMinions(int minionsNumber) throws InsufficientMinions{
		if(minionsNumber > trustedMinions.size())
			throw new InsufficientMinions("Expected minions:" + minionsNumber + " Available:" + trustedMinions.size()); 
		System.out.println("For random, trustedMinions size is:" + trustedMinions.size());

		List<Minion> newHosts = new ArrayList<Minion>();
		Set<Integer> indexSet = new HashSet<Integer>();
		int trustedMinionIndex;
		for(int i = 0;i < minionsNumber;i++){
			trustedMinionIndex = trustedMinionsGenerator.nextInt(trustedMinions.size());
			if(indexSet.contains(trustedMinionIndex)){
				i--;
				continue;
			}
			indexSet.add(trustedMinionIndex);
			newHosts.add(((Entry<String, Minion>)trustedMinionsArray[trustedMinionIndex]).getValue());
		}
		return newHosts;

	}

	public void addApplication(String appId, List<Minion> hosts){
		Application app = new Application(appId);
		this.applications.put(appId, app);
		for(Minion m : hosts){
			m.addApp(app);
			this.appsHosts.put(appId, m);
		}
	}

	public static void main(String[] args){



		System.out.println("Started Monitor");

		String userName = "";
		String sshKey = "";


		String sshArgs = "";
		switch (args.length){
		case 4:
			userName = args[USERNAME_FLAG_INDEX+1];
			sshKey=args[KEY_FLAG_INDEX+1];			
			break;

		default:
			System.out.println("Usage: Monitor -u username -j sshKey");
			System.exit(0);
			break;
		}

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
		new Thread(new DevelopersRequestsHandler(developersServerSocket,monitor,userName,sshKey)).start();
		new Thread(new MinionsRequestsHandler(minionsServerSocket,monitor)).start();

	}
}
