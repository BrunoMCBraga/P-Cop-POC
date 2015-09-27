package developer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import global.Ports;
import global.Messages;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -m monitor -u username -k key -d appDir
 * 
 * 
 * */
//TODO: out should be err
public class DeveloperInterface {
	private static final int MONITOR_FLAG_INDEX=0;
	private static final int USERNAME_FLAG_INDEX=2;
	private static final int KEY_FLAG_INDEX=4;
	private static final int APPDIR_FLAG_INDEX=6;
	private static final String SCP_DIR="/usr/bin/scp";

	
	private static boolean sendApp(String userName, String keyPath, String hostname, String appDir) throws IOException, InterruptedException{
		
		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no  %s %s@%s:~",keyPath, appDir,userName,hostname);
		String[] scpArgsArray = scpArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(scpArgsArray.length+1);
		finalCommand.add(SCP_DIR);

		for (String arg : scpArgsArray)
			finalCommand.add(arg);
		System.out.println(finalCommand);
		ProcessBuilder scpSessionBuilder = new ProcessBuilder(finalCommand);
		Process scpProcess = scpSessionBuilder.start();
		int processResult = scpProcess.waitFor();
		if (processResult != 0){
			//Not sure why but the process runs but returns an abnormal value...
			System.out.println("Scp process returned with abnormal value (" + processResult +") . Try again later.");
			//System.exit(1);
			return false;
		}
		return true;
	}


	public static void main(String[] args){
		System.out.println( System.getProperty("user.dir"));

		String monitor = null;
		String username = null;
		String key=null;
		String appDir = null;

		switch(args.length){
		case 8:
			monitor = args[MONITOR_FLAG_INDEX+1];
			username=args[USERNAME_FLAG_INDEX+1];
			key = args[KEY_FLAG_INDEX+1];
			appDir=args[APPDIR_FLAG_INDEX+1];			
			break;

		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u username");
			System.exit(0);
			break;

		}

		Socket monitorSocket = null;
		try {
			monitorSocket = new Socket(monitor, Ports.MONITOR_DEVELOPER_PORT);
		} catch (IOException e) {
			System.out.println("Unable to create monitor socket:"+e.getMessage());
			System.exit(1);
		}
		
		BufferedReader monitorReader = null;
		BufferedWriter monitorWriter = null;
		
		try {
			monitorReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
			monitorWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		} catch (IOException e) {
			System.out.println("Unable to retrieve socket streams for monitor:"+e.getMessage());
			System.exit(1);
		}
		
		System.out.println("Sending directory to monitor....");
		boolean sendResult=false;
		try {
			sendResult = DeveloperInterface.sendApp(username, key, monitor, appDir);
		} catch (IOException | InterruptedException e) {
			System.out.println("Unable to send files:"+e.getMessage());
			System.exit(1);
		}
		
		if(!sendResult)
			System.err.println("Failed to create scp process.");
		
		Path appPath = FileSystems.getDefault().getPath(appDir);
		try {
			monitorWriter.write(String.format("%s %s %s", Messages.NEW_APP,username,appPath.getFileName().toString()));
			monitorWriter.newLine();
			monitorWriter.flush();
		} catch (IOException e) {
			System.out.println("Unable to send new app request:"+e.getMessage());
			System.exit(1);
		}
		
		String response = null; 
		
		try {
			response = monitorReader.readLine();
		} catch (IOException e) {
			System.out.println("Unable to get OK from monitor:"+e.getMessage());
			System.exit(1);
		}
		
		if(!response.equals(Messages.OK)){
			System.out.println("Failed!");
			System.exit(1);
		}
		
		System.out.println("Success.");
	}
}
