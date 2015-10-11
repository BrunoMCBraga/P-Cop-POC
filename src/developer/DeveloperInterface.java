package developer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;

import admin.AdminInterface;
import exceptions.InvalidMessageException;
import global.Ports;
import global.ProcessBinaries;
import global.Credentials;
import global.Directories;
import global.Messages;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -m monitorHost -u username -k key -d appDir -n instances
 * 
 * 
 * */
//TODO: out should be err
public class DeveloperInterface {
	private static final int MONITOR_HOST_FLAG_INDEX=0;
	private static final int USERNAME_FLAG_INDEX=2;
	private static final int KEY_FLAG_INDEX=4;
	private static final int APPDIR_FLAG_INDEX=6;
	private static final int INSTANCES_FLAG_INDEX=8;
	private static final int DELETE_APP_FLAG_INDEX=8;
	private static final String ADMIN_STORE_NAME = "Developer.jks";
	private static final String MONITORS_TRUST_STORE_NAME = "TrustedMonitors.jks";

	private String monitorHost;
	private String userName;
	private String key;
	private String appDir;
	private int instances;


	public DeveloperInterface(String monitorHost,String userName,String key, String appDir, int instances){
		this.monitorHost = monitorHost;
		this.userName = userName;
		this.key = key;
		this.appDir = appDir;
		this.instances = instances;

	}

	public DeveloperInterface(String monitorHost,String userName,String key, String appDir){
		this(monitorHost,userName,key,appDir,0);

	}

	private boolean sendSyncMessageAndGetResponse(String message) throws UnknownHostException, IOException, InvalidMessageException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException{

		 //Keystore initialization
		System.out.println("Creating keys context...");
		KeyStore ks = KeyStore.getInstance("JKS");
	    FileInputStream keyStoreIStream = new FileInputStream(DeveloperInterface.ADMIN_STORE_NAME);
	    ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

	    //KeyManagerFactory initialization
	    System.out.println("Keystore default algorithm:" + KeyManagerFactory.getDefaultAlgorithm());
	    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
	    kmf.init(ks, Credentials.KEY_PASS.toCharArray());
	    
	    //TrustStore initialization
	    System.out.println("Creating trust context...");
	    KeyStore ts = KeyStore.getInstance("JKS");
	    FileInputStream trustStoreIStream = new FileInputStream(DeveloperInterface.MONITORS_TRUST_STORE_NAME);
	    ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
	    
	    //TrustManagerFactory initialization
	    System.out.println("Trust Default algorithm:" + TrustManagerFactory.getDefaultAlgorithm());
	    TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
	    tmf.init(ts);
	    
	    System.out.println("Creating overall context...");
		SSLContext context = SSLContext.getInstance("TLS");
	    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
	    
	    for(KeyManager tM : kmf.getKeyManagers()){
	    	System.out.println("One key...");
	    	X509Certificate[] certs = ((X509ExtendedKeyManager)tM).getCertificateChain("developer");
	    	System.out.println("Key:" + certs.length);
	    	for(X509Certificate cert : certs)
	    		System.out.println("Certificate key:" + cert.getPublicKey().toString());

	    }

	    for(TrustManager tM : tmf.getTrustManagers()){
	    	System.out.println("One trusted...");
	    	X509Certificate[] certs = ((X509ExtendedTrustManager)tM).getAcceptedIssuers();
	    	System.out.println("Authorized:" + certs.length);
	    	for(X509Certificate cert : certs)
	    		System.out.println("Certificate key:" + cert.getPublicKey().toString());

	    }
	    
	    SSLSocketFactory ssf = context.getSocketFactory();
		
		System.out.println("Connecting");
		Socket monitorSocket = ssf.createSocket(this.monitorHost, Ports.MONITOR_DEVELOPER_PORT);
		System.out.println("Connected");
		BufferedReader monitorReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter monitorWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		System.out.println("Writing message...");

		monitorWriter.write(message);
		monitorWriter.newLine();
		monitorWriter.flush();
		System.out.println("Wrote message...");
		String response = monitorReader.readLine();
		switch(response){
		case Messages.OK:
			return true;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Invalide response:" + response);
		}
		
	}

	private boolean deleteApp() throws UnknownHostException, IOException, InvalidMessageException, UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		Path appPath = FileSystems.getDefault().getPath(appDir);
		boolean messageResult = sendSyncMessageAndGetResponse(String.format("%s %s %s", Messages.DELETE_APP,this.userName,appPath.getFileName().toString()));
	
		if(!messageResult)
			return false;
		return true;
		
	}

	private boolean sendApp() throws IOException, InterruptedException{

		Path appPath = FileSystems.getDefault().getPath(this.appDir);
		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no %s %s@%s:%s%s",this.key, this.appDir,this.userName,this.monitorHost,Directories.APPS_DIR_MONITOR,appPath.getFileName());
		String[] scpArgsArray = scpArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(scpArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SCP_DIR);

		for (String arg : scpArgsArray)
			finalCommand.add(arg);

		ProcessBuilder scpSessionBuilder = new ProcessBuilder(finalCommand);
		Process scpProcess = scpSessionBuilder.start();
		int processResult = scpProcess.waitFor();

		if (processResult != 0){
			return false;
		}
		return true;
	}
	
	private boolean deployApp() throws IOException, InterruptedException, InvalidMessageException, UnrecoverableKeyException, KeyManagementException, KeyStoreException, NoSuchAlgorithmException, CertificateException {

		System.out.println("Sending app to monitor....");
		boolean sendResult= sendApp();
		

		if(!sendResult)
			return false;
		

		Path appPath = FileSystems.getDefault().getPath(this.appDir);
		boolean messageResult = sendSyncMessageAndGetResponse(String.format("%s %s %s %d", Messages.NEW_APP,this.userName,appPath.getFileName().toString(),this.instances));
		
		if(!messageResult)
			return false;
		return true;
			
		
	}

	public static void main(String[] args){
		
		DeveloperInterface devInt = null;

		String monitorHost = null;
		String userName = null;
		String key=null;
		String appDir = null;
		String instances = null;
		boolean commandResult = false;
		//TODO:Flags should be checked
		switch(args.length){
		case 10:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			userName=args[USERNAME_FLAG_INDEX+1];
			key = args[KEY_FLAG_INDEX+1];
			appDir=args[APPDIR_FLAG_INDEX+1];
			instances = args[INSTANCES_FLAG_INDEX+1];
			devInt = new DeveloperInterface(monitorHost, userName, key, appDir,Integer.parseInt(instances));
			try {
				commandResult = devInt.deployApp();
			} catch (IOException | InterruptedException | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
				System.err.println("Failed to deploy app:" + e.getMessage());
				e.printStackTrace();
			}
			break;
		case 9:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			userName=args[USERNAME_FLAG_INDEX+1];
			key = args[KEY_FLAG_INDEX+1];
			appDir=args[APPDIR_FLAG_INDEX+1];
			devInt = new DeveloperInterface(monitorHost, userName, key, appDir);
			try {
				commandResult = devInt.deleteApp();
			} catch (IOException | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
				System.err.println("Failed to delete app:" + e.getMessage());

			}
			break;
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u username [-n num_instances,-r]");
			System.exit(0);
			break;

		}
		
		if(commandResult)
		{
			System.out.println("Success.");
			System.exit(0);
		}
		else
			System.out.println("Failed.");
		System.exit(1);

	}
}
