package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ssl.SSLServerSocketFactory;

import exceptions.UnregisteredMinion;
import global.Messages;
import global.Ports;

public class MinionsRequestsHandler implements Runnable {

	private Monitor monitor;

	public MinionsRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
	}


	private void processMinionRequest(Socket minionSocket) throws IOException{

		BufferedReader socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		if(splittedRequest[0].equals(Messages.REGISTER)){
			monitor.addNewMinion(minionSocket.getInetAddress().getHostAddress());
			System.out.println("Registered:");
		}
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();

	}


	@Override
	public void run() {

		//ServerSocket minionsServerSocket = null;
		//Socket minionSocket = null;
		
		SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
		ServerSocket minionsServerSocket = null;
		
		Socket minionSocket = null;

		try {
			minionsServerSocket = ssf.createServerSocket(Ports.MONITOR_MINION_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for minions:" + e.getMessage());
			System.exit(1);
		}

		while(true){

			try {
				minionSocket = minionsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting minion connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving minion streams:" + e.getMessage());
				continue;
			}

			String minionRequest = null;

			try {
				minionRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving minion sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = minionRequest.split(" ");
			boolean requestResult = true;

			switch(splittedRequest[0]){
			case Messages.REGISTER:
				System.out.println("Registering:" + minionSocket.getInetAddress().getHostAddress());
				monitor.addNewMinion(minionSocket.getInetAddress().getHostAddress());
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
