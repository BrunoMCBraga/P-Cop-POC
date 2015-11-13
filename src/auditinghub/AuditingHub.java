package auditinghub;


import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Hashtable;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import admin.AdminInterface;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import global.Credentials;
import global.Messages;
import global.Ports;

/*
 * AudtingHub -u userName -k key -m monitor -h hostname
 * 
 * */


//TODO:Allow session exit and clean active threads in case of ending or previous failure.
//TODO:Free resources
//TODO:The hub must know if there are ongoing sessions on a node coming from other hubs. Hub agreement or node notification?
public class AuditingHub {

	private static final int HUB_USERNAME_FLAG_INDEX=0;
	private static final int HUB_KEY_FLAG_INDEX=2;
	private static final int MONITOR_FLAG_INDEX = 4;
	private static final int HOSTNAME_FLAG_INDEX = 6;
	private static final String TRUSTED_ADMINS = "TrustedAdmins.jks";


	private String hubUsername;
	private String hubKey;
	private String monitorHost;

	private Map<Long, Thread> temporaryThreads;
	private Map<String, Thread> remoteHostThreadMap;
	private String hostName;
	private byte[] approvedConfiguration;
	private String approvedSHA1;


	public AuditingHub(String hubUserName, String hubKey, String monitorHost, String hostName){

		this.hubUsername = hubUserName;
		this.hubKey = hubKey;
		this.monitorHost = monitorHost;
		this.hostName = hostName;
		this.temporaryThreads = new Hashtable<Long,Thread>();
		this.remoteHostThreadMap = new Hashtable<String,Thread>();

	}

	public String getHubUserName() {
		return this.hubUsername;
	}

	public String getHubKey(){
		return this.hubKey;
	}

	public String getMonitorHost() {
		return this.monitorHost;
	}

	public String getHostName(){
		return this.hostName;
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

	public void removeSession(String remoteHost){
		this.remoteHostThreadMap.remove(remoteHost);
	}


	public void setApprovedConfiguration(String approvedSHA1, byte[] signedConfiguration) {
		this.approvedSHA1 = approvedSHA1;
		this.approvedConfiguration = signedConfiguration;

	}

	public byte[] getApprovedConfiguration() {
		return this.approvedConfiguration;
	}

	public static void main(String[] args) throws IOException, InterruptedException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException{

		System.out.println("Started Audting Hub");

		AuditingHub aH = null;

		String hubUserName;
		String hubKey;
		String monitorHost;
		String hostName = null;

		switch (args.length){
		case 8:
			hubUserName = args[HUB_USERNAME_FLAG_INDEX+1];
			hubKey=args[HUB_KEY_FLAG_INDEX+1];
			monitorHost=args[MONITOR_FLAG_INDEX+1];
			hostName=args[HOSTNAME_FLAG_INDEX+1];
			aH = new AuditingHub(hubUserName, hubKey, monitorHost,hostName);
			break;
		default:
			System.out.println("Usage: AudtingHub -u userName -k key -m monitor -h hostname");
			System.exit(0);
			break;
		}


		new Thread(new AuditorsRequestsHandler(aH)).start();
		while(aH.getApprovedConfiguration() == null);

		ServerSocket hubServerSocket = new ServerSocket(Ports.HUB_LOCAL_PORT);
		Socket newSessionSocket;
		Thread sessionThread;

		while(true){
			newSessionSocket = hubServerSocket.accept();

			//BufferedReader socketReader = new BufferedReader(new InputStreamReader(newSessionSocket.getInputStream())); 
			//BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(newSessionSocket.getOutputStream()));

			//String request = socketReader.readLine();
			//String[] splittedRequest = request.split(" ");
			//boolean requestResult = false;

			//switch(splittedRequest[0]){
			//MANAGE node
			//TODO:Notify minion and monitor and delete ssh process when leaving
			//case Messages.MANAGE:
			System.out.println("Management request.");
			sessionThread = new Thread(new AdminSessionRequestHandler(aH, newSessionSocket));
			aH.temporaryThreads.put(sessionThread.getId(), sessionThread);
			sessionThread.start();
			break;


			//}
		}

	}

	public String getApprovedSHA1() {
		return approvedSHA1;

	}




}
