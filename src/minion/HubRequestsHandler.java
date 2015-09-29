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

/*
 * Receives migration requests: MIGRATE APP NEW_NODE
 * */

public class HubRequestsHandler implements Runnable {


	private ServerSocket loggerControlSocket;

	public HubRequestsHandler() throws IOException {
		this.loggerControlSocket = new ServerSocket(Ports.MINION_HUB_PORT);
	}
	
	
	private boolean purgeMinion() throws IOException, InterruptedException {
		//TODO:put scripts as consts
		String purgeMinionCommand = String.format("sudo ../PurgeMinion.sh");
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

		Socket hubSocket = null;
		
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		

		while(true){

			try {
				hubSocket = loggerControlSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting logger connection:" + e.getMessage());
				continue;
			}
			
			try {
				socketReader = new BufferedReader(new InputStreamReader(hubSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(hubSocket.getOutputStream()));
			} catch (IOException e) {
				System.err.println("Unable to retrieve socket stream:" + e.getMessage());
			}
			
			String monitorRequest= null; 
			
			try {
				socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Failed to read sync line:" + e.getMessage());
				continue;
			}
			
			String[] splittedRequest = monitorRequest.split(" ");
			boolean requestResult = false;

			
			switch(splittedRequest[0]){
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
