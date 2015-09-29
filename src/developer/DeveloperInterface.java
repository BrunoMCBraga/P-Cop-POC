package developer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import exceptions.InvalidMessageException;
import global.Ports;
import global.ProcessBinaries;
import global.Directories;
import global.Messages;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -m monitorHost -u username -k key -d appDir -n instances
 * 
 * 
 * */
//TODO: out should be err
public class DeveloperInterface {
	private static final int MONITOR_HOST_FLAG_INDEX=0;
	private static final int USERNAME_FLAG_INDEX=2;
	private static final int KEY_FLAG_INDEX=4;
	private static final int APPDIR_FLAG_INDEX=6;
	private static final int INSTANCES_FLAG_INDEX=8;
	private static final int DELETE_APP_FLAG_INDEX=8;

	private String monitorHost;
	private String userName;
	private String key;
	private String appDir;
	private int instances;


	public DeveloperInterface(String monitorHost,String userName,String key, String appDir, int instances){
		this.monitorHost = monitorHost;
		this.userName = userName;
		this.key = key;
		this.appDir = appDir;
		this.instances = instances;

	}

	public DeveloperInterface(String monitorHost,String userName,String key, String appDir){
		this(monitorHost,userName,key,appDir,0);

	}

	private boolean sendSyncMessageAndGetResponse(String message) throws UnknownHostException, IOException, InvalidMessageException{

		Socket monitorSocket = new Socket(this.monitorHost, Ports.MONITOR_DEVELOPER_PORT);
		BufferedReader monitorReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter monitorWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		monitorWriter.write(message);
		monitorWriter.newLine();
		monitorWriter.flush();
		
		String response = monitorReader.readLine();
		if(response.equals(Messages.OK))
			return true;
		else if(response.equals(Messages.ERROR))
			return false;
		else throw new InvalidMessageException("Invalide response:" + response);


	}

	private void deleteApp() {
		Path appPath = FileSystems.getDefault().getPath(appDir);
		boolean messageResult = false;
		try {
			messageResult = sendSyncMessageAndGetResponse(String.format("%s %s %s", Messages.DELETE_APP,this.userName,appPath.getFileName().toString()));
		} catch (IOException | InvalidMessageException e) {
			System.err.println("Error when notifying monitor:" + e.getMessage());
			System.exit(1);
		}
	
		if(!messageResult){
			System.err.println("Monitor returned error for delete.");
			System.exit(1);
		}
		
	}

	private boolean sendApp() throws IOException, InterruptedException{

		Path appPath = FileSystems.getDefault().getPath(this.appDir);
		String scpArgs = String.format("-r -i %s -oStrictHostKeyChecking=no %s %s@%s:%s%s",this.key, this.appDir,this.userName,this.monitorHost,Directories.APPS_DIR,appPath.getFileName());
		String[] scpArgsArray = scpArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(scpArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SCP_DIR);

		for (String arg : scpArgsArray)
			finalCommand.add(arg);

		ProcessBuilder scpSessionBuilder = new ProcessBuilder(finalCommand);
		Process scpProcess = scpSessionBuilder.start();
		int processResult = scpProcess.waitFor();

		if (processResult != 0){
			return false;
		}
		return true;
	}
	
	private void deployApp() {

		System.out.println("Sending app to monitor....");
		boolean sendResult=false;
		try {
			sendResult = sendApp();
		} catch (IOException | InterruptedException e) {
			System.out.println("Unable to send files:" + e.getMessage());
			System.exit(1);
		}

		if(!sendResult)
		{
			System.out.println("Sending process returned with abnormal value.");
			System.exit(1);

		}

		Path appPath = FileSystems.getDefault().getPath(this.appDir);
		boolean messageResult = false;
		try {
			messageResult = sendSyncMessageAndGetResponse(String.format("%s %s %s %s", Messages.NEW_APP,this.userName, appPath.getFileName(),this.instances));
		//TODO:Differentiate. In case notification fails, there should be a rollback. 
		} catch (IOException | InvalidMessageException e) {
			System.err.println("Error when notifying monitor:" + e.getMessage());
			System.exit(1);
		}
		
		if(!messageResult){
			System.err.println("Monitor returned error for deploy.");
			System.exit(1);
		}
		
	}

	public static void main(String[] args){
		System.out.println( System.getProperty("user.dir"));
		DeveloperInterface devInt = null;

		String monitorHost = null;
		String userName = null;
		String key=null;
		String appDir = null;
		String instances = null;
		//TODO:Flags should be checked
		switch(args.length){
		case 10:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			userName=args[USERNAME_FLAG_INDEX+1];
			key = args[KEY_FLAG_INDEX+1];
			appDir=args[APPDIR_FLAG_INDEX+1];
			instances = args[INSTANCES_FLAG_INDEX+1];
			devInt = new DeveloperInterface(monitorHost, userName, key, appDir,Integer.parseInt(instances));
			devInt.deployApp();
			break;
		case 9:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			userName=args[USERNAME_FLAG_INDEX+1];
			key = args[KEY_FLAG_INDEX+1];
			appDir=args[APPDIR_FLAG_INDEX+1];
			devInt = new DeveloperInterface(monitorHost, userName, key, appDir);
			devInt.deleteApp();
			break;
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u username [-n num_instances,-r]");
			System.exit(0);
			break;

		}
		
		System.exit(0);

	}
}
