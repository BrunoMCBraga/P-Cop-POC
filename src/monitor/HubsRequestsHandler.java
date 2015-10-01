package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import exceptions.ExistentApplicationId;
import exceptions.InsufficientMinions;
import exceptions.NonExistentApplicationId;
import exceptions.UnregisteredMinion;
import global.Messages;
import global.Ports;

/*
 * Hub sets nodes untrusted and trusted
 * */

public class HubsRequestsHandler implements Runnable {
	
	private Monitor monitor;

	public HubsRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
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
				} catch (UnregisteredMinion e) {
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
