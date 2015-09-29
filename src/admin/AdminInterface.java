package admin;
import java.lang.ProcessBuilder;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import exceptions.InvalidMessageException;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Process;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -a hub -h host -u username -k key
 * 
 * 
 * */
//TODO:detect broken connections
public class AdminInterface {

	private static final int HUB_FLAG_INDEX=0;
	private static final int HOST_FLAG_INDEX=2;
	private static final int USERNAME_FLAG_INDEX=4;
	private static final int ADMIN_KEY_INDEX=6;
	private String userName;
	private String hubHost;
	private String remoteHost;
	private String key;
	private Socket hubSocket;

	public AdminInterface(String userName, String hubHost, String remoteHost, String key){
		this.userName = userName;
		this.hubHost = hubHost;
		this.remoteHost= remoteHost;
		this.key = key;
	}



	private boolean startLocalProxy() throws IOException, InterruptedException{
		String sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -f %s@%s -L %d:%s:%d -N",this.key, this.userName,this.hubHost,Ports.ADMIN_SSH_PORT,this.hubHost,Ports.HUB_LOCAL_PORT);
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);

		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);
		Process proxyProcess = sshSessionBuilder.start();

		int processResult = proxyProcess.waitFor();
		if (processResult != 0){
			return false;
		}

		return true;

	}

	private boolean sendSyncMessageAndGetResponse(String message) throws UnknownHostException, IOException, InvalidMessageException{

		this.hubSocket =  new Socket((String)null, Ports.ADMIN_SSH_PORT);
		BufferedReader adminSessionReader = new BufferedReader(new InputStreamReader(this.hubSocket.getInputStream()));
		BufferedWriter adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.hubSocket.getOutputStream()));

		adminSessionWriter.write(message);
		adminSessionWriter.newLine();
		adminSessionWriter.flush();

		//This detects when the socket is closed....
		String response = adminSessionReader.readLine();
		if(response.equals(Messages.OK))
			return true;
		else if(response.equals(Messages.ERROR))
			return false;
		else throw new InvalidMessageException("Invalide response:" + response);


	}

	private void manageNode() {

		boolean proxyCreationResult = false;
		try {
			proxyCreationResult = startLocalProxy();
		} catch (IOException | InterruptedException e) {
			System.err.println("Failed to create local ssh proxy.");
			System.exit(1);
		}
		
		if(!proxyCreationResult){
			System.err.println("Failed to create local proxy");
			System.exit(1);
		}
			
		boolean syncMessResult = false;
		String manageRequestString = String.format("%s %s %s", Messages.MANAGE,this.userName,this.remoteHost);
		try {
			syncMessResult = sendSyncMessageAndGetResponse(manageRequestString);
		} catch (IOException | InvalidMessageException e) {
			System.err.println("Unable to start admin session:" + e.getMessage());
			System.exit(1);
		}

		if(!syncMessResult){
			System.err.println("Failed to sync with hub");
			System.exit(1);
		}



		String prompt = String.format("%s@%s", this.userName,this.remoteHost);
		BufferedReader adminSessionReader = null; 
		BufferedWriter adminSessionWriter = null;

		try {
			adminSessionReader = new BufferedReader(new InputStreamReader(this.hubSocket.getInputStream()));
			adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.hubSocket.getOutputStream()));
		} catch (IOException e) {
			System.err.println("Failed to obtain socket streams for session:" + e.getMessage());
		}


		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));
		BufferedWriter promptWriter= new BufferedWriter(new OutputStreamWriter(System.out));

		String hostInput = null;
		String hostOutput = null;

		//TODO:Detect ctrl+c
		while (true){
			try {
				hostOutput = adminSessionReader.readLine();
				promptWriter.write(hostOutput);
				promptWriter.newLine();
				promptWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to retrieve hub message:" + e.getMessage());
				continue;
			}
			
			if(!hostOutput.equals(prompt))
				continue;
			
			
			try {
				hostInput = promptReader.readLine();
				adminSessionWriter.write(hostInput);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to send command to hub:" + e.getMessage());
				continue;
			}
			

		}

	}

	public static void main(String[] args){

		AdminInterface aI;

		String userName;
		String hubHost;
		String remoteHost;	
		String key;

		switch (args.length){
		case 8:
			userName = args[USERNAME_FLAG_INDEX+1];
			hubHost=args[HUB_FLAG_INDEX+1];
			remoteHost=args[HOST_FLAG_INDEX+1];
			key = args[ADMIN_KEY_INDEX+1];
			aI = new AdminInterface(userName, hubHost, remoteHost, key);
			aI.manageNode();
			break;
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u userName -k key");
			System.exit(0);
			break;
		}

		System.exit(0);

	}



}
