package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

import exceptions.InsufficientMinions;
import global.Ports;
import global.Directories;
import global.Messages;

public class DevelopersRequestsHandler implements Runnable {

	private static final String SCP_DIR="/usr/bin/scp";
	private static final String SSH_DIR="/usr/bin/ssh";

	private ServerSocket developersServerSokcet;
	private Monitor monitor;
	private String userName;
	private String sshKey;

	public DevelopersRequestsHandler(ServerSocket developersServerSocket, Monitor monitor, String userName, String sshKey) {
		this.developersServerSokcet = developersServerSocket;
		this.monitor = monitor;
		this.userName = userName;
		this.sshKey = sshKey;
	}

	private boolean sendApp(Minion minion, String appDir) throws IOException, InterruptedException{

		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no  ~/%s %s@%s:%s%s",sshKey, appDir,userName,minion.getIpAddress(),Directories.APPS_DIR,appDir);
		String[] scpArgsArray = scpArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(scpArgsArray.length+1);
		finalCommand.add(SCP_DIR);

		for (String arg : scpArgsArray)
			finalCommand.add(arg);
		System.out.println(finalCommand);
		ProcessBuilder scpSessionBuilder = new ProcessBuilder(finalCommand);
		Process scpProcess = scpSessionBuilder.start();
		int processResult = scpProcess.waitFor();
		if (processResult != 0){
			//Not sure why but the process runs but returns an abnormal value...
			System.out.println("Scp process returned with abnormal value (" + processResult +") . Try again later.");
			//System.exit(1);
			return false;
		}
		return true;
	}

	private boolean pickMinionAndDeploy(String appId, int instances) throws UnknownHostException, IOException {
		List<Minion> trustedMinions =  null;

		try {
			trustedMinions = monitor.pickNTrustedMinions(instances);
		} catch (InsufficientMinions e) {
			System.err.println("Failed to pick trusted instances:" + e.getMessage());
		}

		Socket minionSocket =  null;
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		boolean scpResult = true;

		for(Minion m : trustedMinions){
			minionSocket = new Socket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
			socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
			socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
			System.out.println("Uploading app to minion...");
			try {
				scpResult &= sendApp(m,appId);
			} catch (InterruptedException e) {
				System.err.println("Unable to scp files to minion:"+e.getMessage());
			}
			socketWriter.write(String.format("%s %s",Messages.DEPLOY, appId));
			socketWriter.newLine();
			socketWriter.flush();

		}

		//this assumes the deployment works. the error codes returned by process waitfor are not correct and therefore do not allow correct verification.AFAIK
		this.monitor.addApplication(appId, trustedMinions);

		if(scpResult)
			System.out.println("Successfully sent app files for:"+appId);

		if(socketReader.readLine().equals(Messages.OK))
			return true;

		return false;


	}

	private boolean deleteApp(String appId) throws UnknownHostException, IOException {
		List<Minion> appHosts = this.monitor.getHosts(appId);


		Socket minionSocket =  null;
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		boolean result = true;

		for(Minion m : appHosts){
			minionSocket = new Socket(m.getIpAddress(), Ports.MINION_MONITOR_PORT);
			socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
			socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
			System.out.println("Deleting app on minions.");
			socketWriter.write(String.format("%s %s",Messages.DELETE, appId));
			socketWriter.newLine();
			socketWriter.flush();
			if(socketReader.readLine().equals(Messages.OK)){
				result &= true;
				

			}
			else
				result &= false;

		}
		

		if(result)
			this.monitor.deleteApplication(appId);
		



		return false;
	}


	private void processDeveloperRequest(Socket developerSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;


		socketReader = new BufferedReader(new InputStreamReader(developerSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(developerSocket.getOutputStream()));

		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		boolean actionResult = false;
		if(splittedRequest[0].equals(Messages.NEW_APP)){
			System.out.println(String.format("Deploying:%s from:%s",splittedRequest[2],splittedRequest[1]));
			actionResult = pickMinionAndDeploy(splittedRequest[2], Integer.parseInt(splittedRequest[3]));
			if(actionResult == true){
				System.out.println("Deployment confirmation received.");
				socketWriter.write(Messages.OK);
				socketWriter.newLine();
				socketWriter.flush();
			}
		}

		if(splittedRequest[0].equals(Messages.DELETE_APP)){
			System.out.println(String.format("Deleting:%s from:%s",splittedRequest[2],splittedRequest[1]));
			actionResult = deleteApp(splittedRequest[2]);
			if(actionResult == true){
				System.out.println("Deletion confirmation received.");
				socketWriter.write(Messages.OK);
				socketWriter.newLine();
				socketWriter.flush();
			}

		}
	}


	@Override
	public void run() {

		Socket developerSocket = null;

		while(true){

			try {
				developerSocket = developersServerSokcet.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting developer connection:" + e.getMessage());
				try {
					developerSocket.close();
					developerSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processDeveloperRequest(developerSocket);
				developerSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing developer request:"+e.getMessage());
				System.exit(1);

			}

		}		
	}

}
