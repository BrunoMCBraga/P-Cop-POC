package admin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import exceptions.InvalidMessageException;
import global.Messages;

public class CTRLCHandler implements Runnable{

	private Socket hubSocket;
	private Process proxyProcess;

	public CTRLCHandler(Socket hubSocket, Process proxyProcess){
		this.hubSocket = hubSocket;
		this.proxyProcess = proxyProcess;
	}

	//TODO:EXit
	@Override
	public void run() {
		BufferedReader adminSessionReader = null;
		BufferedWriter adminSessionWriter = null;
		
		try {
			adminSessionReader = new BufferedReader(new InputStreamReader(this.hubSocket.getInputStream()));
			adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.hubSocket.getOutputStream()));
		} catch (IOException e) {
			System.err.println("Failed to close SSH session...");
			return;
		} 


		try {
			adminSessionWriter.write(Messages.MANAGE_TEARDOWN);
			adminSessionWriter.newLine();
			adminSessionWriter.flush();
		} catch (IOException e) {
			System.err.println("Failed to send tear session request:"+e.getMessage());
			return;
		}

		//This detects when the socket is closed....
		String response = null;
		try {
			response = adminSessionReader.readLine();
		} catch (IOException e) {
			System.err.println("Failed to get session tear down response:"+e.getMessage());
			return;
		}
		switch(response){
		case Messages.OK:
			System.out.println("Success on tearing session down..");
			break;
		case Messages.ERROR:
			System.err.println("Failed to tear session down.");
			break;
		default:
			System.err.println("Invalid response:" + response);
		}
		
		System.out.println("Destroying proxy process.");
		this.proxyProcess.destroy();
		System.out.println("Proxy process destroyed. Now returning.");
		return;
	}
}
