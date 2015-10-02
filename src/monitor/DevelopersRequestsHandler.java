package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import exceptions.ExistentApplicationId;
import exceptions.InsufficientMinions;
import exceptions.InvalidMessageException;
import exceptions.NonExistentApplicationId;
import global.Ports;
import global.ProcessBinaries;
import minion.MonitorRequestsHandler;
import global.Directories;
import global.Messages;

public class DevelopersRequestsHandler implements Runnable {

	private Monitor monitor;
	private String userName;
	private String sshKey;

	public DevelopersRequestsHandler(Monitor monitor, String userName, String sshKey) throws IOException {
		this.monitor = monitor;
		this.userName = userName;
		this.sshKey = sshKey;
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

	private boolean deployApp(String appId, int instances) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException {

		List<Minion> trustedMinions =  monitor.pickNTrustedMinions(instances);		

		//this assumes the deployment works. the error codes returned by process waitfor are not correct and therefore do not allow correct verification.AFAIK
		
		//TODO:check without adding. If the scp or others fail the application ID reamisn taken.
		this.monitor.addApplication(appId, trustedMinions);

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
			minionSocket = new Socket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
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

	private boolean deleteApp(String appId) throws UnknownHostException, IOException, NonExistentApplicationId, InvalidMessageException {
		
		List<Minion> appHosts = this.monitor.getHosts(appId);
		this.monitor.deleteApplication(appId);

		Socket minionSocket =  null;
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		boolean result = true;
		String deleteResult;

		for(Minion m : appHosts){
			System.out.println("Deleting app on minion:" + m.getIpAddress());
			minionSocket = new Socket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
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



	//TODO: Maybe create threads to answer clients in parallel.
	@Override
	public void run() {

		ServerSocket developersServerSocket = null;
		Socket developerSocket = null;
		try {
			developersServerSocket = new ServerSocket(Ports.MONITOR_DEVELOPER_PORT);
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
				} catch (InterruptedException | IOException | NumberFormatException | InsufficientMinions | ExistentApplicationId | InvalidMessageException e) {
					System.err.println("Unable to deploy:" + e.getMessage());
				}

				break;
			//DELETE_APP user appId
			case Messages.DELETE_APP:
				System.out.println("Deleting:" + splittedRequest[2]);
				try {
					requestResult = deleteApp(splittedRequest[2]);
				} catch (IOException | NonExistentApplicationId | InvalidMessageException e) {
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
