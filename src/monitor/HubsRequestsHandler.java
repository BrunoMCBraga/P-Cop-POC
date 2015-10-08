package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import exceptions.ExistentApplicationId;
import exceptions.InsufficientMinions;
import exceptions.InvalidMessageException;
import exceptions.NonExistentApplicationId;
import exceptions.UnregisteredMinion;
import global.Directories;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;

/*
 * Hub sets nodes untrusted and trusted
 * */

public class HubsRequestsHandler implements Runnable {

	private Monitor monitor;
	private String userName;
	private String sshKey;

	public HubsRequestsHandler(Monitor monitor,String userName, String sshKey) throws IOException {
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

	private boolean deployAppOnMinion(String appId, Minion host) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException {

		boolean scpResult = true;
		String deployResult;
		//TODO: i should scp all first and then order deploy. Also,this should be atomic.
		System.out.println("Uploading app to minion...");
		scpResult &= sendApp(host, appId);
		
		Socket minionSocket =  minionSocket = new Socket(host.getIpAddress(), Ports.MINION_MONITOR_PORT);
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
	private boolean spawnReplacementInstances(Minion untrustedMinion) throws UnknownHostException, IOException, InsufficientMinions, InterruptedException, ExistentApplicationId, InvalidMessageException {
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
			spawnResult &= deployAppOnMinion(application.getKey(), trustedMinions.get(tempTrustedSet.toArray()[0]));
			if(!spawnResult)
				return false;
		}
		
		return true;

	}

	@Override
	public void run() {

		ServerSocket hubsServerSocket = null;
		Socket hubSocket = null;

		try {
			hubsServerSocket = new ServerSocket(Ports.MONITOR_HUB_PORT);
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
					this.monitor.setMinionTrusted(splittedRequest[1]);
				} catch (UnregisteredMinion e) {
					System.err.println("Unable to set trusted:" + e.getMessage());
					requestResult = false;
				}
				break;
				//SET_UNTRUSTED hostIP
			case Messages.SET_UNTRUSTED:
				System.out.println("Setting:" + splittedRequest[1] + " untrusted.");
				try {
					this.monitor.setMinionUntrusted(splittedRequest[1]);
					spawnReplacementInstances(this.monitor.getUntrustedMinion(splittedRequest[1]));
				} catch (UnregisteredMinion | IOException | InsufficientMinions | InterruptedException | ExistentApplicationId | InvalidMessageException e) {
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
