package monitor;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import exceptions.ExistentApplicationId;
import exceptions.InsufficientMinions;
import exceptions.NonExistentApplicationId;
import exceptions.UnregisteredMinion;

import java.util.Random;
import java.util.Set;

import global.Credentials;
import global.Ports;

/*
 * Usage Monitor -u username -k sshKey -h hostName
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
	private static final int HOSTNAME_FLAG_INDEX=4;

	//Maps IPs to Minions. The array is for random indexing.
	private Map<String,Minion> trustedMinions;
	//Entry<String,Minion>
	private Object[] trustedMinionsArray;


	private Map<String,Minion> untrustedMinions;
	private Random trustedMinionsIndexGenerator;
	private Map<String,List<Minion>> appsHosts;
	private Map<String,Application> applications;
	private String hostName;
	private String userName;
	private String sshKey;
	private byte[] approvedConfiguration = null;
	private String approvedSHA1;
	private String minionsApprovedSHA1;

	public Monitor(String userName, String sshKey, String hostName){
		
		this.userName = userName;
		this.sshKey = sshKey;
		this.hostName = hostName;

		this.trustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsArray = this.trustedMinions.entrySet().toArray();
		this.untrustedMinions = new Hashtable<String,Minion>();
		this.trustedMinionsIndexGenerator = new Random();
		this.appsHosts = new Hashtable<String,List<Minion>>();
		this.applications = new Hashtable<String,Application>();

	}

	public String getSSHKey() {
		return sshKey;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public String getHostName() {
		return hostName;
	}
	
	public void addNewMinion(String newMinionIpAddress){

		Minion newMinion = new Minion(newMinionIpAddress);
		trustedMinions.put(newMinion.getIpAddress(), newMinion);
		this.trustedMinionsArray = trustedMinions.entrySet().toArray();
		System.out.println("Added:" + newMinion.getIpAddress() + "Size:" + trustedMinions.size());
		//check the boolean??

	}

	public void removeMinion(String minionIpAddress) throws UnregisteredMinion{

		if(trustedMinions.remove(minionIpAddress) == null){
			if(untrustedMinions.remove(minionIpAddress) == null)
				throw new UnregisteredMinion("The minion:" + minionIpAddress + " is not registered");
		}
		else
			this.trustedMinionsArray = trustedMinions.entrySet().toArray();
		//check the boolean??
	}

	//TODO:1. Remove application IDs on minion. 2. Remove Minion from AppId->Minions. 3. Migrate..
	public void setMinionUntrusted(String ipAddress) throws UnregisteredMinion{
		Minion trustedMinion = trustedMinions.remove(ipAddress);
		if (trustedMinion != null){
			untrustedMinions.put(ipAddress, trustedMinion);
			this.trustedMinionsArray = trustedMinions.entrySet().toArray();
			return;
		}
		throw new UnregisteredMinion("Cannot untrusted unregistered minion:" + ipAddress);
	}


	//TODO:store hostnames instead of IPs
	public void setMinionTrusted(String ipAddress) throws UnregisteredMinion{
		Minion untrustedMinion = untrustedMinions.remove(ipAddress);
		if (untrustedMinion != null){
			trustedMinions.put(ipAddress, untrustedMinion);
			this.trustedMinionsArray = trustedMinions.entrySet().toArray();
			for(Entry<String,Application> applications : untrustedMinion.getApplications().entrySet())
				this.appsHosts.get(applications.getKey()).remove(untrustedMinion);
			return;
		}

		throw new UnregisteredMinion("Cannot trust unregistered minion:" + ipAddress);
	}

	public Minion pickTrustedMinion() throws InsufficientMinions{

		if(trustedMinions.size() == 0)
			throw new InsufficientMinions("Expected minions:1 Available:0"); 

		int trustedIndex = trustedMinionsIndexGenerator.nextInt(trustedMinions.size());
		return ((Entry<String, Minion>)trustedMinionsArray[trustedIndex]).getValue();

	}

	public List<Minion> pickNTrustedMinions(int minionsNumber) throws InsufficientMinions{

		if(minionsNumber > trustedMinions.size())
			throw new InsufficientMinions("Expected minions:" + minionsNumber + " .Available:" + trustedMinions.size()); 

		List<Minion> newHosts = new ArrayList<Minion>();
		Set<Integer> indexSet = new HashSet<Integer>();
		int trustedMinionIndex;

		for(int i = 0;i < minionsNumber;i++){
			trustedMinionIndex = trustedMinionsIndexGenerator.nextInt(trustedMinions.size());
			if(indexSet.contains(trustedMinionIndex)){
				i--;
				continue;
			}
			indexSet.add(trustedMinionIndex);
			newHosts.add(((Entry<String, Minion>)trustedMinionsArray[trustedMinionIndex]).getValue());
		}
		return newHosts;

	}

	public void addApplication(String appId, List<Minion> hosts) throws ExistentApplicationId{

		if(this.applications.containsKey(appId))
			throw new ExistentApplicationId("The application id:" + appId + " already exists.");

		Application app = new Application(appId);
		this.applications.put(appId, app);
		List<Minion> minionsList = new ArrayList<Minion>();
		this.appsHosts.put(appId, minionsList);

		for(Minion m : hosts){
			m.addApp(app);
			minionsList.add(m);
			System.out.println("Added host:"+m.getIpAddress()+ " with app id:" + appId);

		}
	}

	public void deleteApplication(String appId) throws NonExistentApplicationId {

		if(this.applications.remove(appId)==null)
			throw new NonExistentApplicationId("Non-existent application id:" + appId);

		List<Minion> minions = this.appsHosts.remove(appId);
		for(Minion host : minions)
			host.removeApp(appId);

	}

	public List<Minion> getHosts(String appId){
		return appsHosts.get(appId);		
	}
	
	public Minion getUntrustedMinion(String ipAddress) {
		return this.untrustedMinions.get(ipAddress);
	}

	public Map<String, Minion> getTrustedMinions() {
		return this.trustedMinions;
	}

	public void setApprovedConfiguration(String pcrSHA1, byte[] signedConfiguration) {
		this.approvedSHA1 = pcrSHA1;
		this.approvedConfiguration = signedConfiguration;
		 
	}
	
	public byte[] getApprovedConfiguration() {
		return this.approvedConfiguration;
	}
	
	public void setApprovedConfigurationForMinions(String pcrSHA1) {
		this.minionsApprovedSHA1 = pcrSHA1;
	
	}
	

	
	public static void main(String[] args) throws IOException{



		System.out.println("Started Monitor");
		
		
		Monitor monitor = null;
		String userName = "";
		String sshKey = "";

		
		switch (args.length){
		case 6:
			//TODO:set username and sshKey as fields
			monitor = new Monitor(args[USERNAME_FLAG_INDEX+1], args[KEY_FLAG_INDEX+1], args[HOSTNAME_FLAG_INDEX+1]);
			break;

		default:
			System.out.println("Usage: Monitor -u username -j sshKey");
			System.exit(0);
		}
		
		ServerSocket minionsServerSocket = null;

		new Thread(new AuditorsRequestsHandler(monitor)).start();

		while(monitor.getApprovedConfiguration() == null){
			System.out.println("Pending approval.");
			
		}
		
		new Thread(new DevelopersRequestsHandler(monitor)).start();
		new Thread(new HubsRequestsHandler(monitor)).start();
		new Thread(new MinionsRequestsHandler(monitor)).start();

		try {
			synchronized (Thread.currentThread()) {
				Thread.currentThread().wait();
			}
		} catch (InterruptedException e) {
			System.exit(0);
		}
	}

	public String getMinionsSHA1() {
		return this.minionsApprovedSHA1;
	}

	public String getApprovedSHA1() {
		return this.approvedSHA1;
	}

	

	

	



}
