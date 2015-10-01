package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import global.Messages;
import global.Ports;
import global.Scripts;

/*
 * Receives migration requests: MIGRATE APP NEW_NODE
 * */

public class HubRequestsHandler implements Runnable {


	private boolean purgeMinion() throws IOException, InterruptedException {
		//TODO:put scripts as constants
		String purgeMinionCommand = String.format("sudo ../%s",Scripts.PURGE_MINION);
		String[]  purgeMinionCommandArray = purgeMinionCommand.split(" ");


		ProcessBuilder purgeMinionProcessBuilder = new ProcessBuilder(purgeMinionCommandArray);
		Process deleteAppCommandProcess = purgeMinionProcessBuilder.start();

		int deleteContainerResult = deleteAppCommandProcess.waitFor();
		if (deleteContainerResult != 0){
			return false;
		}

		return true;
	}
	
	
	

	@Override
	public void run() {

		ServerSocket hubServerSocket = null;
		try {
			hubServerSocket = new ServerSocket(Ports.MINION_HUB_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for hub:" + e.getMessage());
			System.exit(1);
		}
		Socket hubSocket = null;
		
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		

		while(true){

			try {
				hubSocket = hubServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting hub connection:" + e.getMessage());
				continue;
			}
			
			try {
				socketReader = new BufferedReader(new InputStreamReader(hubSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(hubSocket.getOutputStream()));
			} catch (IOException e) {
				System.err.println("Unable to retrieve socket stream for hub:" + e.getMessage());
			}
			
			String monitorRequest= null; 
			
			try {
				monitorRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Failed to read sync line from hub:" + e.getMessage());
				continue;
			}
			
			String[] splittedRequest = monitorRequest.split(" ");
			boolean requestResult = false;

			
			switch(splittedRequest[0]){
			//PURGE
			case Messages.PURGE:
				System.out.println("Purging");
				try {
					requestResult = purgeMinion();
					//TODO:Interrupted exception should not count as an error...??
				} catch (IOException | InterruptedException e) {
					System.err.println("Unable to purge");

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
