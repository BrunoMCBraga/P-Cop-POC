package auditinghub;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import exceptions.InvalidMessageException;
import exceptions.RejectedConfiguration;
import global.AttestationConstants;
import global.Credentials;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;
import monitor.DevelopersRequestsHandler;

//TODO:Detect mistyping errors. Since the session is not interactive, errors seem not to be being sent.
public class AdminSessionRequestHandler implements Runnable {

	private static final String UNCOMMITED_LOGS_DIR="../../Logs/Uncommited/";

	private static final String MONITORS_TRUST_STORE = "TrustedMonitors.jks";

	private static final String MINIONS_TRUST_STORE = "TrustedMinions.jks";

	AuditingHub auditingHubInstance;
	private Socket adminToHubSocket;
	private String hubUserName;
	private String hubKey;

	private String adminUserName;
	private String remoteHost;
	private String monitorHost;

	private String promptString;
	private Process auditingHubToNodeSessionProcess;
	private Logger logger;

	private String hubStore;

	private String hostName;


	public AdminSessionRequestHandler(AuditingHub auditingHubInstance,Socket adminToHubSocket, String adminUserName,String remoteHost){

		this.auditingHubInstance = auditingHubInstance;
		this.adminToHubSocket = adminToHubSocket;
		this.adminUserName = adminUserName;
		this.remoteHost = remoteHost;

		this.hubUserName = auditingHubInstance.getHubUserName();
		this.hubKey = auditingHubInstance.getHubKey();
		this.monitorHost = auditingHubInstance.getMonitorHost();
		this.hostName = auditingHubInstance.getHostName();
		this.hubStore = auditingHubInstance.getHostName() + ".jks";

	}


	private void launchSessionProcess() throws IOException{


		String sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -t %s@%s -vvvvv",this.hubKey, this.hubUserName,this.remoteHost);
		System.out.println(sshArgs);
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);

		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);

		//TODO: what if it fails? Cannot wait but must check?
		this.auditingHubToNodeSessionProcess = sshSessionBuilder.start();


	}

	private void launchLogger() throws SecurityException, IOException{

		Path logsDirPath = FileSystems.getDefault().getPath(UNCOMMITED_LOGS_DIR);
		if(Files.notExists(logsDirPath))
			Files.createDirectories(logsDirPath);



		this.logger = Logger.getLogger(String.format("%s.%s.%s", AuditingHub.class.getCanonicalName(),adminUserName,remoteHost));
		this.logger.setLevel(Level.ALL);
		LocalDateTime current = LocalDateTime.now();
		String logFileName = String.format("%s/%s@%s__%d-%s-%d_%d-%d-%d.log", logsDirPath.toString(),this.adminUserName,this.remoteHost,current.getDayOfMonth(),current.getMonth().toString(),current.getYear(),current.getHour(),current.getMinute(),current.getSecond());
		FileHandler handler = new FileHandler(logFileName,true);
		handler.setFormatter(new SimpleFormatter());
		this.logger.addHandler(handler);


	}


	private boolean setNodeUntrusted() throws InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException{
		
		//Keystore initialization
	    KeyStore ks = KeyStore.getInstance("JKS");
	    FileInputStream keyStoreIStream = new FileInputStream(this.hubStore);
	    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

	    //KeyManagerFactory initialization
	    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
	    
	    //TrustStore initialization
	    KeyStore ts = KeyStore.getInstance("JKS");
	    FileInputStream trustStoreIStream = new FileInputStream(AdminSessionRequestHandler.MONITORS_TRUST_STORE);
	    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
	    
	    //TrustManagerFactory initialization
	    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    tmf.init(ts);
	    
		SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	 
	    SSLSocketFactory ssf = context.getSocketFactory();
	    
		Socket monitorSocket = ssf.createSocket(InetAddress.getByName(this.monitorHost), Ports.MONITOR_HUB_PORT);
		BufferedReader monitorSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter monitorSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

		monitorSessionWriter.write(String.format("%s %s", Messages.SET_UNTRUSTED, InetAddress.getByName(this.remoteHost).getHostAddress()));
		monitorSessionWriter.newLine();
		monitorSessionWriter.flush();

		String monitorResponse = monitorSessionReader.readLine();
		switch (monitorResponse) {
		case Messages.OK:
			System.out.println("Successful migration.");
			return true;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Unexpected sync value from monitor:" + monitorResponse);
		}
	}
	
	private boolean purgeMinion() throws IOException, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException{
		
		//Keystore initialization
	    KeyStore ks = KeyStore.getInstance("JKS");
	    FileInputStream keyStoreIStream = new FileInputStream(this.hubStore);
	    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

	    //KeyManagerFactory initialization
	    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
	    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
	    
	    //TrustStore initialization
	    KeyStore ts = KeyStore.getInstance("JKS");
	    FileInputStream trustStoreIStream = new FileInputStream(AdminSessionRequestHandler.MINIONS_TRUST_STORE);
	    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
	    
	    //TrustManagerFactory initialization
	    TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	    tmf.init(ts);
	    
		SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	 
	    SSLSocketFactory ssf = context.getSocketFactory();
		
		Socket minionSocket = ssf.createSocket(this.remoteHost, Ports.MINION_HUB_PORT);
		BufferedReader minionSessionReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter minionSessionWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

		minionSessionWriter.write(Messages.PURGE);
		minionSessionWriter.newLine();
		minionSessionWriter.flush();

		String minionResponse = minionSessionReader.readLine();
		switch (minionResponse) {
		case Messages.OK:
			System.out.println("Successful purge.");
			return true;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Unexpected sync value from minion:" + minionResponse);
		}
		
	}
	
	private boolean launchManagementSession() throws IOException, InvalidMessageException, NoSuchAlgorithmException, CertificateException, KeyStoreException, UnrecoverableKeyException, KeyManagementException {

		//TODO:register hub
		
		if(!setNodeUntrusted())
			return false;
		
		if(!purgeMinion())
			return false;
		


		launchSessionProcess();///TODO:catch exceptions here/...
		launchLogger();

		BufferedReader adminSessionReader = new BufferedReader(new InputStreamReader(this.adminToHubSocket.getInputStream()));
		BufferedWriter adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.adminToHubSocket.getOutputStream()));

		BufferedReader hostSessionReader = new BufferedReader(new InputStreamReader(this.auditingHubToNodeSessionProcess.getInputStream()));
		BufferedWriter hostSessionWriter = new BufferedWriter(new OutputStreamWriter(this.auditingHubToNodeSessionProcess.getOutputStream()));

		System.out.println("Session created. Sending prompt to admin...");
		this.promptString = String.format("[%s@%s]>", this.adminUserName,this.remoteHost);


		//The command must be put in "". Otherwise, the console will fail to run the echo. Also, the "" must be escaped.
		hostSessionWriter.write("echo " + "\"" + this.promptString + "\"");
		hostSessionWriter.newLine();
		hostSessionWriter.flush();

		StringBuilder hostCompleteResponseBuilder = new StringBuilder();
		String hostInput = null;
		String hostOutput = null;


		while (true){
			try {
				hostOutput = hostSessionReader.readLine();
				hostCompleteResponseBuilder.append(hostOutput);
				hostCompleteResponseBuilder.append(System.lineSeparator());
				adminSessionWriter.write(hostOutput);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();

				if(!hostOutput.equals(this.promptString))
					continue;

			} catch (IOException e) {
				System.err.println("Failed to bridge Host->Admin:" + e.getMessage());
				continue; //TODO:Think this through
			}


			this.logger.log(Level.ALL, "Host->Admin:\n"+hostCompleteResponseBuilder.toString());
			hostCompleteResponseBuilder.setLength(0);

			try {
				hostInput = adminSessionReader.readLine();
				//1.delete process.2.delete active session from maps.
				if(hostInput.equals(Messages.MANAGE_TEARDOWN))
				{
					System.out.println("Session ended.");
					this.auditingHubToNodeSessionProcess.destroy();
					this.auditingHubInstance.removeSession(this.remoteHost);
					return true;
				}
				hostSessionWriter.write(hostInput+"; echo " + "\"" + this.promptString + "\"");
				hostSessionWriter.newLine();
				hostSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to bridge Admin->Host:" + e.getMessage());
				continue;
			}


			this.logger.log(Level.ALL, "Admin->Host:"+hostInput+System.lineSeparator());
			//TODO:Return...

		}

	}

	private void processAttestation(Socket adminSocket) throws IOException, InvalidMessageException, RejectedConfiguration {

		BufferedReader attestationReader = attestationReader = new BufferedReader(new InputStreamReader(adminSocket.getInputStream()));
		BufferedWriter attestationWriter = attestationWriter = new BufferedWriter(new OutputStreamWriter(adminSocket.getOutputStream()));

		String[] attestationRequestArray =  attestationReader.readLine().split(" ");
		if(attestationRequestArray[0].equals(Messages.ATTEST)){

			attestationWriter.write(String.format("%s %s %s", Messages.QUOTE, AttestationConstants.QUOTE, AttestationConstants.QUOTE));
			attestationWriter.newLine();
			attestationWriter.flush();
		}
		else 		
			throw new InvalidMessageException("Expected:" + Messages.ATTEST + ". Received:" + attestationRequestArray[0]);


		if(attestationReader.readLine().equals(Messages.ERROR))
			throw new RejectedConfiguration("Admin rejected platform attestation.");

	}
	
	@Override
	public void run() {

		try {
			processAttestation(this.adminToHubSocket);
		} catch (IOException | InvalidMessageException | RejectedConfiguration e2) {
			System.err.println("Failed admin attestation:" + e2.getMessage());
			System.exit(1);
		}
		BufferedReader adminSessionReader;
		BufferedWriter adminSessionWriter;
		try {
			adminSessionReader = new BufferedReader(new InputStreamReader(this.adminToHubSocket.getInputStream()));
			adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.adminToHubSocket.getOutputStream()));
		} catch (IOException e) {
			System.err.println("Error obtaining admin session streams:" + e.getMessage());
			return;
		}

		//TODO:PURGE before entering...
		//TODO:if writes fail, session lists may be outdated
		boolean launchManagementSessionResult = false;
		if(this.auditingHubInstance.checkPermissionAndUnqueue(this.remoteHost)){
			try {
				try {
					adminSessionWriter.write(Messages.OK);
					adminSessionWriter.newLine();
					adminSessionWriter.flush();
				} catch (IOException e) {
					System.err.println("Failed to signal OK for session start to admin:" + e.getMessage());
					this.adminToHubSocket.close();
					return;
				}
				//I assume that this method will not fail. Otherwise the admin must be notified also.
				//It returns true if session is ended orderly and false if the other nodes do not respond properly...
				launchManagementSessionResult = launchManagementSession();
			} catch (IOException | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
				System.err.println("Failed to launch management session:" + e.getMessage());
				try {
					this.adminToHubSocket.close();
				} catch (IOException e1) {

				}
			}
		}

		if(launchManagementSessionResult)
			try {
				adminSessionWriter.write(Messages.OK);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to signal session ending:" + e.getMessage());
			}

		else
			try {
				adminSessionWriter.write(Messages.ERROR);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to signal session ending error:" + e.getMessage());
			}

	}


}
