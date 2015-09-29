package auditinghub;


import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Map;

import admin.AdminInterface;

import java.io.IOException;
import global.Ports;

/*
 * AudtingHub -u userName -k key
 * 
 * */


//TODO:Allow session exit and clean active threads in case of ending or previous failure.
//TODO:Free resources
//TODO:The hub must know if there are ongoing sessions on a node coming from other hubs. Hub agreement or node notification?
public class AuditingHub {

	private static final int HUB_USERNAME_FLAG_INDEX=0;
	private static final int HUB_KEY_FLAG_INDEX=2;

	private String hubUsername;
	private String hubKey;
	private Map<Long, Thread> temporaryThreads;
	private Map<String, Thread> remoteHostThreadMap;


	public AuditingHub(String hubUserName, String hubKey){
		
		this.hubUsername = hubUserName;
		this.hubKey = hubKey;
		this.temporaryThreads = new Hashtable<Long,Thread>();
		this.remoteHostThreadMap = new Hashtable<String,Thread>();
		
	}
	
	public String getHubUserName() {
		return this.hubUsername;
	}
	
	public String getHubKey(){
		return this.hubKey;
	}
	
	//Check if there is another session to a given host
	public boolean checkPermissionAndUnqueue(String remoteHost){
		
		Thread sessionThread = this.remoteHostThreadMap.get(remoteHost);
		
		if(sessionThread != null)
			return false;
		else{
			this.remoteHostThreadMap.put(remoteHost, temporaryThreads.remove(Thread.currentThread().getId()));
			return true;
		}
	}
	
	public static void main(String[] args) throws IOException, InterruptedException{

		System.out.println("Started Audting Hub");
		
		AuditingHub aH = null;

		String hubUserName;
		String hubKey;

		switch (args.length){
		case 8:
			hubUserName = args[HUB_USERNAME_FLAG_INDEX+1];
			hubKey=args[HUB_KEY_FLAG_INDEX+1];
			aH = new AuditingHub(hubUserName, hubKey);
			break;
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u userName -k key");
			System.exit(0);
			break;
		}

	
		ServerSocket hubServerSocket = new ServerSocket(Ports.HUB_LOCAL_PORT);
		Socket newSessionSocket;
		Thread sessionThread;

		while(true){
			newSessionSocket = hubServerSocket.accept();
			System.out.println("Connection from:" + newSessionSocket.getRemoteSocketAddress());
			sessionThread = new Thread(new AdminSession(aH, newSessionSocket));
			aH.temporaryThreads.put(sessionThread.getId(), sessionThread);
			sessionThread.start();
		}

	}
	

}
