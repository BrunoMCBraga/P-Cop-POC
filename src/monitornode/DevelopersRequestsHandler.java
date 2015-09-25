package monitornode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import global.Ports;
import global.Messages;

public class DevelopersRequestsHandler implements Runnable {

	private ServerSocket developersServerSokcet;
	private Monitor monitor;

	public DevelopersRequestsHandler(ServerSocket developersServerSocket, Monitor monitor) {
		this.developersServerSokcet = developersServerSocket;
		this.monitor = monitor;
	}

	private boolean pickMinionAndDeploy(String appId) throws UnknownHostException, IOException {
		Minion trustedMinion =  monitor.pickTrustedMinion();
		Socket minionSocket = new Socket(trustedMinion.getIpAddress(), Ports.MINION_MONITOR_PORT);
		BufferedReader socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
		System.out.println("Uploading app to minion...");
		socketWriter.write(String.format("%s %s",Messages.DEPLOY, appId));
		socketWriter.newLine();
		socketWriter.flush();
		
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
			System.out.println(String.format("Deploying:%s from:%s",splittedRequest[1],splittedRequest[2]));
			deploymentResult = pickMinionAndDeploy(splittedRequest[1]);

		}
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();

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
