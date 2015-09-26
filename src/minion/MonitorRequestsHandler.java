package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import global.Messages;

/*
 *-Receives deployment requests: Deploy App_Folder 
 *-Picks code and interacts with docker to create container
 *-
 * */
//TODO:Turn this into a task if necessary
public class MonitorRequestsHandler implements Runnable {

	private ServerSocket monitorServerSocket;
	
	public MonitorRequestsHandler(ServerSocket monitorSocket) {
		this.monitorServerSocket = monitorSocket;
	}
	
	private void processMonitorRequest(Socket monitorSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		
		socketReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		if(splittedRequest[0].equals(Messages.DEPLOY))
			System.out.println("Deploying:" + splittedRequest[1]);

		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();
	}
	
	
	@Override
	public void run() {
		
		Socket monitorSocket = null;
		
		while(true){
			
			try {
				monitorSocket = monitorServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting master connection:" + e.getMessage());
				try {
					monitorSocket.close();
					monitorServerSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processMonitorRequest(monitorSocket);
				monitorSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing monitor request:"+e.getMessage());
				System.exit(1);

			}
			
		}
		
	}
	
	
	
	
}
