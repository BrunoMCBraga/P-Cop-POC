package admin;
import java.lang.ProcessBuilder;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import global.Ports;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Process;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -a hub -h host -u username
 * 
 * 
 * */

public class AdminInterface {
	
	private static final int HUB_FLAG_INDEX=0;
	private static final int HOST_FLAG_INDEX=2;
	private static final int USERNAME_FLAG_INDEX=4;
	private static final String ADMIN_CREDENTIALS_PATH="ssh_key";
	private static final String SSH_DIR="/usr/bin/ssh";

	public static void main(String[] args) throws IOException, InterruptedException{
	
		String userName = "";
		String hubHost = "";
		String remoteHost="";
				
		String sshArgs = "";
		switch (args.length){
		case 6:
			userName = args[USERNAME_FLAG_INDEX+1];
			hubHost=args[HUB_FLAG_INDEX+1];
			remoteHost=args[HOST_FLAG_INDEX+1];
			
			sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -f %s@%s -L %d:%s:%d -N",ADMIN_CREDENTIALS_PATH, userName,hubHost,Ports.ADMIN_SSH_PORT,hubHost,Ports.HUB_LOCAL_PORT);
			
			break;
		
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u username");
			System.exit(0);
			break;
		}
		
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);
		
		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);

	    Process proxyProcess = sshSessionBuilder.start();
		int processResult = proxyProcess.waitFor();
		if (processResult != 0){
			//Not sure why but the process runs but returns an abnormal value...
			System.out.println("Proxy creation process returned with abnormal value (" + processResult +") . Try again later.");
			//System.exit(1);
		}
		System.out.println("Local SSH proxy created on port:"+Ports.ADMIN_SSH_PORT);
		Socket sessionSocket = new Socket((String)null,Ports.ADMIN_SSH_PORT);
		BufferedReader adminSessionReader = new BufferedReader(new InputStreamReader(sessionSocket.getInputStream()));
		BufferedWriter adminSessionWriter = new BufferedWriter(new OutputStreamWriter(sessionSocket.getOutputStream()));
		
		String requestString = String.format("%s@%s", userName,remoteHost);
		String prompt = String.format("[%s]>", requestString);

		adminSessionWriter.write(requestString);
		adminSessionWriter.newLine();
		adminSessionWriter.flush();
		System.out.println("Destination host and username sent.");
		
		String hostInput = null;
		String hostOutput = null;
		
		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));
		BufferedWriter promptWriter= new BufferedWriter(new OutputStreamWriter(System.out));
				
		
		while (true){
			while ((hostOutput = adminSessionReader.readLine()) != null){
				promptWriter.write(hostOutput);
				promptWriter.newLine();
				if(hostOutput.equals(prompt))
					break;

			}
			
			promptWriter.flush();
			System.out.println("Waiting for admin commands...");
			hostInput = promptReader.readLine();
			adminSessionWriter.write(hostInput);
			adminSessionWriter.newLine();
			adminSessionWriter.flush();
			
				
		}
		
	}

}
