package monitornode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import global.Messages;

/*
 * Hub sets nodes untrusted and trusted
 * */

public class HubsRequestsHandler implements Runnable {

	

	private ServerSocket hubsServerSocket;
	private Monitor monitor;

	public HubsRequestsHandler(ServerSocket hubsServerSocket, Monitor monitor) {
		this.hubsServerSocket = hubsServerSocket;
		this.monitor = monitor;
	}

	private void processHubRequest(Socket loggerSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		
		socketReader = new BufferedReader(new InputStreamReader(loggerSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(loggerSocket.getOutputStream()));
		
		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		if(splittedRequest[0].equals(Messages.SET_UNTRUSTED))
			System.out.println("Untrusting:" + splittedRequest[1]);
		else if(splittedRequest[0].equals(Messages.SET_TRUSTED))
			System.out.println("Trusting:" + splittedRequest[1]);
		
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();
		
	}
	

	@Override
	public void run() {

		Socket hubSocket = null;

		while(true){

			try {
				hubSocket = hubsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting hub connection:" + e.getMessage());
				try {
					hubSocket.close();
					hubsServerSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processHubRequest(hubSocket);
				hubSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing hub request:"+e.getMessage());
				System.exit(1);

			}

		}		
	}

}
