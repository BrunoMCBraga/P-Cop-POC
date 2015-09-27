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

import global.Ports;
import global.Messages;

public class DevelopersRequestsHandler implements Runnable {
	
	private static final String SCP_DIR="/usr/bin/scp";
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

		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no  ~/%s %s@%s:~",sshKey, appDir,userName,minion.getIpAddress());
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

	private boolean pickMinionAndDeploy(String appFolder) throws UnknownHostException, IOException {
		Minion trustedMinion =  monitor.pickTrustedMinion();
		Socket minionSocket = new Socket(trustedMinion.getIpAddress(), Ports.MINION_MONITOR_PORT);
		BufferedReader socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
		System.out.println("Uploading app to minion...");
		boolean scpResult = false;
		try {
			scpResult = sendApp(trustedMinion,appFolder);
		} catch (InterruptedException e) {
			System.err.println("Unable to scp files to minion:"+e.getMessage());
		}
		socketWriter.write(String.format("%s %s",Messages.DEPLOY, appFolder));
		socketWriter.newLine();
		socketWriter.flush();
		
		if(scpResult)
			System.out.println("Successfully sent app files for:"+appFolder);

		if(socketReader.readLine().equals(Messages.OK))
			return true;

		return false;


	}
	private void processDeveloperRequest(Socket developerSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;


		socketReader = new BufferedReader(new InputStreamReader(developerSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(developerSocket.getOutputStream()));

		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		boolean deploymentResult = false;
		if(splittedRequest[0].equals(Messages.NEW_APP)){
			System.out.println(String.format("Deploying:%s from:%s",splittedRequest[2],splittedRequest[1]));
			deploymentResult = pickMinionAndDeploy(splittedRequest[2]);

		}
		if(deploymentResult == true){
			System.out.println("Deployment confirmation received.");
			socketWriter.write(Messages.OK);
			socketWriter.newLine();
			socketWriter.flush();
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
