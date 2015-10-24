package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import auditinghub.AuditingHub;
import exceptions.ExistentApplicationId;
import exceptions.InsufficientMinions;
import exceptions.InvalidMessageException;
import exceptions.NonExistentApplicationId;
import exceptions.RejectedConfiguration;
import global.Ports;
import global.ProcessBinaries;
import minion.MonitorRequestsHandler;
import global.AttestationConstants;
import global.Credentials;
import global.Directories;
import global.Messages;

public class DevelopersRequestsHandler implements Runnable {

	private static final String DEVELOPERS_TRUST_STORE = "TrustedDevelopers.jks";
	private static final String MINIONS_TRUST_STORE = "TrustedMinions.jks";
	private Monitor monitor;
	private String userName;
	private String sshKey;
	private String monitorHostName;
	private String monitorStore;

	public DevelopersRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
		this.userName = monitor.getUserName();
		this.sshKey = monitor.getSSHKey();
		this.monitorHostName = monitor.getHostName();
		this.monitorStore = monitor.getHostName()+".jks";
	}

	private boolean sendApp(Minion minion, String appDir) throws IOException, InterruptedException{

		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no ../../%s%s %s@%s:%s%s",sshKey, Directories.APPS_DIR_MONITOR,appDir,userName,minion.getIpAddress(),Directories.APPS_DIR_MINION,appDir);
		String[] scpArgsArray = scpArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(scpArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SCP_DIR);

		for (String arg : scpArgsArray)
			finalCommand.add(arg);
		System.out.println(finalCommand);
		ProcessBuilder scpSessionBuilder = new ProcessBuilder(finalCommand);
		scpSessionBuilder.redirectError(new File("ErrorScpMonitor.txt"));
		Process scpProcess = scpSessionBuilder.start();
		int processResult = scpProcess.waitFor();
		if (processResult != 0){
			System.out.println("Scp process returned with abnormal value (" + processResult +") . Try again later.");
			return false;
		}
		return true;
	}

	private boolean deployApp(String appId, int instances) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {

		List<Minion> trustedMinions =  monitor.pickNTrustedMinions(instances);		

		//this assumes the deployment works. the error codes returned by process waitfor are not correct and therefore do not allow correct verification.AFAIK

		//TODO:check without adding. If the scp or others fail the application ID reamisn taken.
		this.monitor.addApplication(appId, trustedMinions);

		//Keystore initialization
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream keyStoreIStream = new FileInputStream(this.monitorStore);
		ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//KeyManagerFactory initialization
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, Credentials.KEY_PASS.toCharArray());

		//TrustStore initialization
		KeyStore ts = KeyStore.getInstance("JKS");
		FileInputStream trustStoreIStream = new FileInputStream(DevelopersRequestsHandler.MINIONS_TRUST_STORE);
		ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//TrustManagerFactory initialization
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ts);

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		SSLSocketFactory ssf = context.getSocketFactory();

		Socket minionSocket =  null;
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		boolean scpResult = true;
		String deployResult;
		//TODO: i should scp all first and then order deploy. Also,this should be atomic.
		for(Minion m : trustedMinions){
			System.out.println("Uploading app to minion...");
			scpResult &= sendApp(m,appId);

			if(!scpResult)
				return false;
			minionSocket = ssf.createSocket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
			socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
			socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
			socketWriter.write(String.format("%s %s",Messages.DEPLOY, appId));
			socketWriter.newLine();
			socketWriter.flush();

			deployResult = socketReader.readLine();

			switch(deployResult){
			case Messages.OK:
				continue;
			case Messages.ERROR:
				return false;
			default:
				throw new InvalidMessageException("Invalide response:" + deployResult);
			}	

		}


		return scpResult;


	}

	private boolean deleteApp(String appId) throws UnknownHostException, IOException, NonExistentApplicationId, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {

		List<Minion> appHosts = this.monitor.getHosts(appId);
		this.monitor.deleteApplication(appId);

		//Keystore initialization
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream keyStoreIStream = new FileInputStream(monitorStore);
		ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//KeyManagerFactory initialization
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, Credentials.KEY_PASS.toCharArray());

		//TrustStore initialization
		KeyStore ts = KeyStore.getInstance("JKS");
		FileInputStream trustStoreIStream = new FileInputStream(DevelopersRequestsHandler.MINIONS_TRUST_STORE);
		ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//TrustManagerFactory initialization
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ts);

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		SSLSocketFactory ssf = context.getSocketFactory();

		Socket minionSocket =  null;
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		boolean result = true;
		String deleteResult;

		for(Minion m : appHosts){
			System.out.println("Deleting app on minion:" + m.getIpAddress());
			minionSocket = ssf.createSocket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
			socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
			socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
			socketWriter.write(String.format("%s %s",Messages.DELETE, appId));
			socketWriter.newLine();
			socketWriter.flush();

			deleteResult = socketReader.readLine();

			switch(deleteResult){
			case Messages.OK:
				continue;
			case Messages.ERROR:
				return false;
			default:
				throw new InvalidMessageException("Invalide response:" + deleteResult);
			}	
		}

		return result;
	}


	private void processAttestation(Socket developerSocket) throws IOException, InvalidMessageException, RejectedConfiguration {

		BufferedReader attestationReader = attestationReader = new BufferedReader(new InputStreamReader(developerSocket.getInputStream()));
		BufferedWriter attestationWriter = attestationWriter = new BufferedWriter(new OutputStreamWriter(developerSocket.getOutputStream()));

		String[] attestationRequestArray =  attestationReader.readLine().split(" ");
		if(attestationRequestArray[0].equals(Messages.ATTEST)){

			attestationWriter.write(String.format("%s %s %s", Messages.QUOTE, AttestationConstants.QUOTE, DatatypeConverter.printHexBinary(this.monitor.getApprovedConfiguration())));
			attestationWriter.newLine();
			attestationWriter.flush();
		}
		else 		
			throw new InvalidMessageException("Expected:" + Messages.ATTEST + ". Received:" + attestationRequestArray[0]);


		if(attestationReader.readLine().equals(Messages.ERROR))
			throw new RejectedConfiguration("Developer rejected platform attestation.");

	}

	//TODO: Maybe create threads to answer clients in parallel.
	@Override
	public void run() {

		//ServerSocket developersServerSocket = null;

		SSLContext context = null;
		try {
			//Keystore initialization
			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream keyStoreIStream = new FileInputStream(this.monitorStore);
			ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//KeyManagerFactory initialization
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, Credentials.KEY_PASS.toCharArray());

			//TrustStore initialization
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream trustStoreIStream = new FileInputStream(DevelopersRequestsHandler.DEVELOPERS_TRUST_STORE);
			ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//TrustManagerFactory initialization
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for developers:" + e1.getMessage());
			System.exit(0);
		}


		SSLServerSocketFactory ssf = context.getServerSocketFactory();

		ServerSocket developersServerSocket = null;
		Socket developerSocket = null;

		try {
			developersServerSocket = ssf.createServerSocket(Ports.MONITOR_DEVELOPER_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for developers:" + e.getMessage());
			System.exit(1);
		}



		while(true){

			try {
				developerSocket = developersServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting developer connection:" + e.getMessage());
				continue;
			}

			try {
				processAttestation(developerSocket);
			} catch (IOException | InvalidMessageException | RejectedConfiguration e1) {
				System.err.println("Monitor attestation failed on developer request:" + e1.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(developerSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(developerSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving streams:" + e.getMessage());
				continue;
			}


			String developerRequest = null;

			try {
				developerRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving sync message:" + e.getMessage());
				e.printStackTrace();
				continue;
			}

			String[] splittedRequest = developerRequest.split(" ");
			boolean requestResult = false;

			switch(splittedRequest[0]){
			//NEW_APP user appId instances
			case Messages.NEW_APP:
				System.out.println("Deploying:" + splittedRequest[2]);
				try {
					requestResult = deployApp(splittedRequest[2],Integer.parseInt(splittedRequest[3]));
				} catch (InterruptedException | IOException | NumberFormatException | InsufficientMinions | ExistentApplicationId | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
					System.err.println("Unable to deploy:" + e.getMessage());
				}

				break;
				//DELETE_APP user appId
			case Messages.DELETE_APP:
				System.out.println("Deleting:" + splittedRequest[2]);
				try {
					requestResult = deleteApp(splittedRequest[2]);
				} catch (IOException | NonExistentApplicationId | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
					System.err.println("Unable to delete:"+splittedRequest[2]);
				}
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
