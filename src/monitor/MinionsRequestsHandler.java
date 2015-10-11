package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import exceptions.UnregisteredMinion;
import global.Credentials;
import global.Messages;
import global.Ports;

public class MinionsRequestsHandler implements Runnable {

	private static final String MINIONS_TRUST_STORE = "TrustedMinions.jks";
	private Monitor monitor;
	private String hostName;
	private String monitorStore;

	public MinionsRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
		this.hostName = monitor.getHostName();
		this.monitorStore = monitor.getHostName()+".jks";
	}




	@Override
	public void run() {

		
		
		SSLContext context = null;
		try {
			//Keystore initialization
			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream keyStoreIStream = new FileInputStream(this.monitorStore);
		    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		    //KeyManagerFactory initialization
		    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
		    
		    //TrustStore initialization
		    KeyStore ts = KeyStore.getInstance("JKS");
		    FileInputStream trustStoreIStream = new FileInputStream(MinionsRequestsHandler.MINIONS_TRUST_STORE);
		    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		    
		    //TrustManagerFactory initialization
		    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		    tmf.init(ts);
		    
			context = SSLContext.getInstance("TLS");
		    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for minions:" + e1.getMessage());
			System.exit(0);
		}
		
		SSLServerSocketFactory ssf = context.getServerSocketFactory();
		ServerSocket minionsServerSocket = null;
		Socket minionSocket = null;

		try {
			minionsServerSocket = ssf.createServerSocket(Ports.MONITOR_MINION_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for minions:" + e.getMessage());
			System.exit(1);
		}

		while(true){

			try {
				minionSocket = minionsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting minion connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving minion streams:" + e.getMessage());
				continue;
			}

			String minionRequest = null;

			try {
				minionRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving minion sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = minionRequest.split(" ");
			boolean requestResult = true;

			switch(splittedRequest[0]){
			case Messages.REGISTER:
				System.out.println("Registering:" + minionSocket.getInetAddress().getHostAddress());
				monitor.addNewMinion(minionSocket.getInetAddress().getHostAddress());
				break;

			}


			if(requestResult){
				System.out.println("Success");
				try {
					socketWriter.write(Messages.OK);
					socketWriter.newLine();
					socketWriter.flush();
				} catch (IOException e) {
					System.err.println("Failed to send OK:" + e.getMessage());
				}
			}
			else{
				System.out.println("Failed");

				try {
					socketWriter.write(Messages.ERROR);
					socketWriter.newLine();
					socketWriter.flush();
				} catch (IOException e) {
					System.err.println("Failed to send ERROR:" + e.getMessage());
				}
			}
		}		
	}

}
