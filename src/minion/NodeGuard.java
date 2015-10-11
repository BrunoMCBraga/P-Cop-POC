package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import exceptions.InvalidMessageException;
import global.Credentials;
import global.Messages;
import global.Ports;
import monitor.DevelopersRequestsHandler;

/*
 * NodeGuard -m monitorHostName -h hostName
 * In terms of application deployment, files will not be transmitted through this channel but using SSH.This is just a control channel
 * How it word:
 * 1.Nodes contact master so it learns of their address
 * 2.connection closes
 * 3.Guard listens for control messages from the monitor or logger.
 * 
 * Monitor messages are: monitor:.....\n while logger are logger:.....\n
 * */

public class NodeGuard {

	private static final int MONITOR_HOST_FLAG_INDEX=0;
	private static final int HOSTNAME_FLAG_INDEX = 2;
	private static final String MONITORS_TRUST_STORE = "TrustedMonitors.jks";
	private String monitorHost;
	private String hostName;
	private String minionStore;

	public NodeGuard(String monitorHost, String hostName){
		this.monitorHost = monitorHost;
		this.hostName = hostName;
		this.minionStore = hostName + ".jks";
	}
	
	public String getHostName() {
		return hostName;
	}

	private boolean startMonitorHandler() throws UnknownHostException, IOException, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
		
		//Keystore initialization
	    KeyStore ks = KeyStore.getInstance("JKS");
	    FileInputStream keyStoreIStream = new FileInputStream(this.minionStore);
	    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

	    //KeyManagerFactory initialization
	    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
	    
	    //TrustStore initialization
	    KeyStore ts = KeyStore.getInstance("JKS");
	    FileInputStream trustStoreIStream = new FileInputStream(NodeGuard.MONITORS_TRUST_STORE);
	    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
	    
	    //TrustManagerFactory initialization
	    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    tmf.init(ts);
	    
		SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	 
	    SSLSocketFactory ssf = context.getSocketFactory();

		Socket monitorSocket = ssf.createSocket(this.monitorHost,Ports.MONITOR_MINION_PORT);

		BufferedReader monitorSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter monitorSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		monitorSessionWriter.write(Messages.REGISTER);
		monitorSessionWriter.newLine();
		monitorSessionWriter.flush();



		String monitorResponse = monitorSessionReader.readLine();
		
		switch(monitorResponse){
		case Messages.OK:
			new Thread(new MonitorRequestsHandler(this)).start();
			return true;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Invalide response:" + monitorResponse);
		}	

	}


	private boolean startHubHandler() throws IOException {
		new Thread(new HubRequestsHandler(this)).start();
		return true;
	}


	public static void main(String[] args){

		System.out.println("Started Node Guard");
		System.setProperty("javax.net.ssl.keyStore", Credentials.MINION_KEYSTORE);
		System.setProperty("javax.net.ssl.keyStorePassword", Credentials.KEYSTORE_PASS);

		String monitorHost;
		String hostName;
		NodeGuard nodeGuard = null;
		boolean handlersStartResult = true;

		switch(args.length){
		case 4:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			hostName = args[HOSTNAME_FLAG_INDEX+1];
			nodeGuard = new NodeGuard(monitorHost,hostName);
			try {
				handlersStartResult &= nodeGuard.startMonitorHandler();
				handlersStartResult &= nodeGuard.startHubHandler();
			} catch (IOException | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
				System.err.println("Failed to begin server processes:" + e.getMessage());
				System.exit(1);
			}
			break;
		default:
			System.err.println("NodeGuard -m monitorHostName");
			System.exit(0);
			break;

		}

		
		if(handlersStartResult)
			try {
				synchronized (Thread.currentThread()) {
					Thread.currentThread().wait();

				}
			} catch (InterruptedException e) {
				System.exit(0);
			}

		
	}



}
