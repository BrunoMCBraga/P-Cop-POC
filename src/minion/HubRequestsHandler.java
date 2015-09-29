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
		this.loggerControlSocket = new ServerSocket(Ports.MINION_HUB_PORT);;
	}
	
	
	private void processMonitorRequest(Socket monitorSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		
		socketReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		if(splittedRequest[0].equals(Messages.MIGRATE))
			System.out.println(String.format("Migrating:%s->%s",splittedRequest[1],splittedRequest[2]));
		
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();
		
	}
	

	@Override
	public void run() {

		Socket loggerSocket = null;

		while(true){

			try {
				loggerSocket = loggerControlSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting logger connection:" + e.getMessage());
				try {
					loggerSocket.close();
					loggerControlSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processMonitorRequest(loggerSocket);
				loggerSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing logger request:"+e.getMessage());
				System.exit(1);

			}

		}		
	}

}
