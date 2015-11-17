package monitor;

import java.awt.RenderingHints.Key;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import java.util.Set;

import exceptions.ExistentApplicationId;
import exceptions.FailedAttestation;
import exceptions.InsufficientMinions;
import exceptions.InvalidMessageException;
import exceptions.NonExistentApplicationId;
import exceptions.UnregisteredMinion;
import global.AttestationConstants;
import global.Credentials;
import global.Directories;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;

/*
 * Hub sets nodes untrusted and trusted
 * */

public class HubsRequestsHandler implements Runnable {

	private static final String HUBS_TRUST_STORE = "TrustedHubs.jks";
	private static final String MINIONS_TRUST_STORE = "TrustedMinions.jks";
	private Monitor monitor;
	private String userName;
	private String sshKey;
	private String hostName;
	private String monitorStore;

	public HubsRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
		this.userName = monitor.getUserName();
		this.sshKey = monitor.getSSHKey();
		this.hostName = monitor.getHostName();
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

	private boolean deployAppOnMinion(String appId, Minion host) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, KeyManagementException, UnrecoverableKeyException {

		boolean scpResult = true;
		String deployResult;
		//TODO: i should scp all first and then order deploy. Also,this should be atomic.
		System.out.println("Uploading app to minion...");
		scpResult &= sendApp(host, appId);
		
		//Keystore initialization
	    KeyStore ks = KeyStore.getInstance("JKS");
	    FileInputStream keyStoreIStream = new FileInputStream(this.monitorStore);
	    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

	    //KeyManagerFactory initialization
	    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
	    
	    //TrustStore initialization
	    KeyStore ts = KeyStore.getInstance("JKS");
	    FileInputStream trustStoreIStream = new FileInputStream(HubsRequestsHandler.MINIONS_TRUST_STORE);
	    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
	    
	    //TrustManagerFactory initialization
	    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    tmf.init(ts);
	    
		SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	 
	    SSLSocketFactory ssf = context.getSocketFactory();
		
		Socket minionSocket = ssf.createSocket(host.getIpAddress(), Ports.MINION_MONITOR_PORT);
		BufferedReader socketReader = socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter socketWriter = socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
		socketWriter.write(String.format("%s %s",Messages.DEPLOY, appId));
		socketWriter.newLine();
		socketWriter.flush();
		
		deployResult = socketReader.readLine();
		
		switch(deployResult){
		case Messages.OK:
			return scpResult;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Invalide response:" + deployResult);
		}	



	}
	
	//1.Get all trusted nodes
	//2.For every app on the node, get the nodes where it is running
	//3.Perform a difference betwwen them to obtain the nodes not running that app.
	//4.Pick one node and start deployment.
	private boolean spawnReplacementInstances(Minion untrustedMinion) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException, KeyManagementException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		Map<String,Minion> trustedMinions = this.monitor.getTrustedMinions();
		Set<String> trustedSet = trustedMinions.keySet();
		List<Minion> applicationHosts;
		Set<String> tempTrustedSet;
		boolean spawnResult = true;
		
		for(Entry<String, Application> application: untrustedMinion.getApplications().entrySet()){
			//TODO:here i should use minions and not their addresses but i have implement hashcode.Better play safe.
			tempTrustedSet = new HashSet<String>(trustedSet);
			applicationHosts = this.monitor.getHosts(application.getKey());
			tempTrustedSet.removeAll(applicationHosts);
			if(tempTrustedSet.size() == 0)
				throw new InsufficientMinions("No more minions available for migration");
			spawnResult &= deployAppOnMinion(application.getKey(), trustedMinions.get(tempTrustedSet.toArray()[0]));
			if(!spawnResult)
				return false;
		}
		
		return true;

	}
	
	private void attestMinion(String minionHost) throws IOException, InvalidMessageException, FailedAttestation, KeyStoreException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, SignatureException, InvalidKeySpecException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

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
		    FileInputStream trustStoreIStream = new FileInputStream(HubsRequestsHandler.MINIONS_TRUST_STORE);
		    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		    
		    //TrustManagerFactory initialization
		    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		    tmf.init(ts);
		    
			context = SSLContext.getInstance("TLS");
		    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL context for minions:" + e1.getMessage());
			System.exit(0);
		}
		
		SSLSocketFactory ssf = context.getSocketFactory();
		Socket minionSocket = null;

		try {
			minionSocket = ssf.createSocket(minionHost,Ports.MINION_MONITOR_PORT);
		} catch (IOException e) {
			System.err.println("Failed to socket for minion for attestation:" + e.getMessage());
			System.exit(1);
		}
		
		
		byte[] signedConfig = null;
		//While approved configuration is unavaiable, wait.
		while((signedConfig = this.monitor.getApprovedConfiguration()) == null){
			System.out.println("NULL");
		}
		

		//Keystore initialization
		//KeyStore ks = KeyStore.getInstance("JKS");
		//FileInputStream keyStoreIStream = new FileInputStream(this.MINIONS_TRUST_STORE);
		//ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		//Certificate auditorCert = ks.getCertificate("Auditor");
		//keyStoreIStream.close();
		//Signature rsa = Signature.getInstance("SHA1withRSA"); 
		//rsa.initVerify(auditorCert);


		BufferedReader minionAttestationReader = minionAttestationReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter minionAttestationWriter = minionAttestationWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

		minionAttestationWriter.write(String.format("%s %s", Messages.ATTEST,AttestationConstants.NONCE));
		minionAttestationWriter.newLine();
		minionAttestationWriter.flush();

		String[] splittedResponse = minionAttestationReader.readLine().split(" ");
		byte[] tpmPubKeyBytes = Base64.getDecoder().decode(AttestationConstants.TPM_PUB_KEY.getBytes());
		X509EncodedKeySpec spec = new X509EncodedKeySpec(tpmPubKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey tpmPubKey = keyFactory.generatePublic(spec);
		
		Cipher cipher = Cipher.getInstance("RSA");   
	    cipher.init(Cipher.DECRYPT_MODE, tpmPubKey);  
	    String decryptedQuote = DatatypeConverter.printHexBinary(cipher.doFinal(DatatypeConverter.parseHexBinary(splittedResponse[1])));


		//rsa.update(splittedResponse[1].getBytes());

		
		if(splittedResponse[0].equals(Messages.QUOTE)){
			if(decryptedQuote.equals(AttestationConstants.NONCE+this.monitor.getMinionsSHA1())){
				minionAttestationWriter.write(Messages.OK);
				minionAttestationWriter.newLine();
				minionAttestationWriter.flush();
				return;
			}
		}
		else 
				throw new InvalidMessageException("Expected:" + Messages.QUOTE + ".Received:" + splittedResponse[0]);

		minionAttestationWriter.write(Messages.ERROR);
		minionAttestationWriter.newLine();
		minionAttestationWriter.flush();
		throw new FailedAttestation("Minion has config:" + splittedResponse[1] + ". Expected" + AttestationConstants.QUOTE);


	}

	@Override
	public void run() {

		//ServerSocket hubsServerSocket = null;
		
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
		    FileInputStream trustStoreIStream = new FileInputStream(HubsRequestsHandler.MINIONS_TRUST_STORE);
		    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		    
		    //TrustManagerFactory initialization
		    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		    tmf.init(ts);
		    
			context = SSLContext.getInstance("TLS");
		    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for hubs:" + e1.getMessage());
			System.exit(0);
		}
		
		SSLServerSocketFactory ssf = context.getServerSocketFactory();
		ServerSocket hubsServerSocket = null;
		Socket hubSocket = null;

		try {
			hubsServerSocket = ssf.createServerSocket(Ports.MONITOR_HUB_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for hub:" + e.getMessage());
			System.exit(1);
		}

		while(true){

			try {
				hubSocket = hubsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting hub connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(hubSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(hubSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving hub streams:" + e.getMessage());
				continue;
			}

			String hubRequest = null;

			try {
				hubRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving hub sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = hubRequest.split(" ");
			boolean requestResult = true;

			switch(splittedRequest[0]){
			//SET_TRUSTED hostIP
			case Messages.SET_TRUSTED:
				System.out.println("Setting:" + splittedRequest[1] + " trusted.");
				try {
					attestMinion(splittedRequest[1]);
					this.monitor.setMinionTrusted(splittedRequest[1]);
				} catch (UnregisteredMinion | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException | SignatureException | IOException | InvalidMessageException | FailedAttestation | InvalidKeySpecException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
					System.err.println("Unable to set trusted:" + e.getMessage());
					requestResult = false;
				}
				break;
				//SET_UNTRUSTED hostIP
			case Messages.SET_UNTRUSTED:
				System.out.println("Setting:" + splittedRequest[1] + " untrusted.");
				try {
					this.monitor.setMinionUntrusted(splittedRequest[1]);
					requestResult &= spawnReplacementInstances(this.monitor.getUntrustedMinion(splittedRequest[1]));
				} catch (UnregisteredMinion | IOException | InsufficientMinions | InterruptedException | ExistentApplicationId | InvalidMessageException | KeyManagementException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
					System.err.println("Unable to set untrusted:" + e.getMessage());
					requestResult = false;
				}
				break;
				//TODO:Default on wrong sync messages
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
