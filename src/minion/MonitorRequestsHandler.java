package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import global.Credentials;
import global.Directories;
import global.Messages;
import global.Ports;
import global.Scripts;
import monitor.DevelopersRequestsHandler;
import monitor.Minion;

/*
 *-Receives deployment requests: Deploy App_Folder 
 *-Picks code and interacts with docker to create container
 *-
 * */
//TODO:Turn this into a task if necessary
//TODO:Thread pools
public class MonitorRequestsHandler implements Runnable {


	private static final String MONITORS_TRUST_STORE = "TrustedMonitors.jks";
	private NodeGuard nodeGuard;
	private String hostName;
	private String minionStore;

	MonitorRequestsHandler(NodeGuard nodeGuard) {
		this.nodeGuard = nodeGuard;
		this.hostName = nodeGuard.getHostName();
		this.minionStore = nodeGuard.getHostName() + ".jks";
	}

	private boolean deployApp(String appId) throws IOException, InterruptedException{

		String createContainerCommand = String.format("sudo docker build -t %s-container ../../%s%s",appId,Directories.APPS_DIR_MINION,appId);
		String deployContainerCommand = String.format("sudo docker run -p 80 -d --name %s %s-container",appId,appId);


		String[]  createContainerCommandArray = createContainerCommand.split(" ");
		String[]  deployContainerCommandArray = deployContainerCommand.split(" ");


		ProcessBuilder createConainerProcessBuilder = new ProcessBuilder(createContainerCommandArray);
		createConainerProcessBuilder.redirectError(new File("ErrorOnContainerCreate.txt"));
		Process createContainerProcess = createConainerProcessBuilder.start();
		int processResult = createContainerProcess.waitFor();
		if (processResult != 0){
			return false;
		}
		ProcessBuilder deployConainerProcessBuilder = new ProcessBuilder(deployContainerCommandArray);
		deployConainerProcessBuilder.redirectError(new File("ErrorOnBuild.txt"));
		Process deployProcess = deployConainerProcessBuilder.start();
		processResult += deployProcess.waitFor();

		return processResult==0?true:false;
	}

	private boolean deleteApp(String appId) throws IOException, InterruptedException {

		String deleteAppCommand = String.format("sudo ../%s %s", Scripts.DELETE_APP, appId);
		String[]  deleteAppCommandArray = deleteAppCommand.split(" ");


		ProcessBuilder deleteAppProcessBuilder = new ProcessBuilder(deleteAppCommandArray);
		Process deleteAppCommandProcess = deleteAppProcessBuilder.start();

		int deleteContainerResult = deleteAppCommandProcess.waitFor();
		if (deleteContainerResult != 0){
			return false;
		}

		return true;
	}

	@Override
	public void run() {

		//ServerSocket monitorServerSocket = null;

		SSLContext context = null;
		try {
			//Keystore initialization
			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream keyStoreIStream = new FileInputStream(this.minionStore);
			ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//KeyManagerFactory initialization
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, Credentials.KEY_PASS.toCharArray());

			//TrustStore initialization
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream trustStoreIStream = new FileInputStream(MonitorRequestsHandler.MONITORS_TRUST_STORE);
			ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//TrustManagerFactory initialization
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for monitors:" + e1.getMessage());
			System.exit(0);
		}


		SSLServerSocketFactory ssf = context.getServerSocketFactory();

		ServerSocket monitorServerSocket = null;

		try {
			monitorServerSocket = ssf.createServerSocket(Ports.MINION_MONITOR_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for monitor:" + e.getMessage());
			System.exit(1);
		}
		Socket monitorSocket = null;


		while(true){

			try {
				monitorSocket = monitorServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting monitor connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving master streams:" + e.getMessage());
				continue;
			}

			String monitorRequest = null;

			try {
				monitorRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = monitorRequest.split(" ");
			boolean requestResult = false;

			switch(splittedRequest[0]){
			//DEPLOY appId
			case Messages.DEPLOY:
				System.out.println("Deploying:" + splittedRequest[1]);
				try {
					requestResult = deployApp(splittedRequest[1]);
				} catch (InterruptedException | IOException e) {
					System.err.println("Unable to deploy:" + e.getMessage());
				}

				break;
				//DELETE appId
			case Messages.DELETE:
				System.out.println("Deleting:" + splittedRequest[1]);
				try {
					requestResult = deleteApp(splittedRequest[1]);
				} catch (InterruptedException | IOException e) {
					System.err.println("Unable to delete:"+splittedRequest[1]);
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
