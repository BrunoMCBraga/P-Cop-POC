package monitornode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;

import global.Messages;

public class MinionsRequestsHandler implements Runnable {

	private ServerSocket minionsControlSocket;
	private Monitor monitor;

	public MinionsRequestsHandler(ServerSocket minionsControlSocket, Monitor monitor) {
		this.minionsControlSocket = minionsControlSocket;
		this.monitor = monitor;
	}
	
	
	private void processMinionRequest(Socket minionSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		
		socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));
		
		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		if(splittedRequest[0].equals(Messages.REGISTER)){
			monitor.addNewMinion(new Minion(minionSocket.getInetAddress().getHostAddress()));
			System.out.println("Registered:" + splittedRequest[1]);
		}
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();
		
	}
	

	@Override
	public void run() {

		Socket minionSocket = null;

		while(true){

			try {
				minionSocket = minionsControlSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting minion connection:" + e.getMessage());
				try {
					minionSocket.close();
					minionsControlSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processMinionRequest(minionSocket);
				minionSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing minion request:"+e.getMessage());
				System.exit(1);

			}

		}		
	}

}
