package auditinghub;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import global.Messages;
import global.ProcessBinaries;

//TODO:Detect mistyping errors. Since the session is not interactive, erros seem not to be being sent.
public class AdminSession implements Runnable {

	private static final String UNCOMMITED_LOGS_DIR="Logs/Uncommited/";

	AuditingHub auditingHubInstance;
	private Socket adminToHubSocket;
	private String hubUserName;
	private String hubKey;

	private String adminUserName;
	private String remoteHost;
	private String promptString;
	private Process auditingHubToNodeSessionProcess;
	private Logger logger;


	public AdminSession(AuditingHub auditingHubInstance,Socket adminToHubSocket){

		this.auditingHubInstance = auditingHubInstance;
		this.adminToHubSocket = adminToHubSocket;
		this.hubUserName = auditingHubInstance.getHubUserName();
		this.hubKey = auditingHubInstance.getHubKey();


	}


	private void launchSessionProcess() throws IOException{


		String sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -t %s@%s -vvvvv",this.hubKey+this.remoteHost, this.hubUserName,this.remoteHost);
		System.out.println(sshArgs);
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);

		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);

		//TODO: what if it fails? Cannot wait but must check?
		this.auditingHubToNodeSessionProcess = sshSessionBuilder.start();


	}

	private void launchLogger() throws SecurityException, IOException{

		Path logsDirPath = FileSystems.getDefault().getPath(UNCOMMITED_LOGS_DIR);
		if(Files.notExists(logsDirPath))
			Files.createDirectories(logsDirPath);



		this.logger = Logger.getLogger(String.format("%s.%s.%s", AuditingHub.class.getCanonicalName(),adminUserName,remoteHost));
		this.logger.setLevel(Level.ALL);
		LocalDateTime current = LocalDateTime.now();
		String logFileName = String.format("%s/%s@%s_%d-%s-%d_%d-%d-%d.log", logsDirPath.toString(),this.adminUserName,this.remoteHost,current.getDayOfMonth(),current.getMonth().toString(),current.getYear(),current.getHour(),current.getMinute(),current.getSecond());
		FileHandler handler = new FileHandler(logFileName,true);
		handler.setFormatter(new SimpleFormatter());
		this.logger.addHandler(handler);


	}


	private boolean launchManagementSession() throws IOException {

		launchSessionProcess();
		launchLogger();

		BufferedReader adminSessionReader = new BufferedReader(new InputStreamReader(adminToHubSocket.getInputStream()));
		BufferedWriter adminSessionWriter = new BufferedWriter(new OutputStreamWriter(adminToHubSocket.getOutputStream()));

		BufferedReader hostSessionReader = new BufferedReader(new InputStreamReader(this.auditingHubToNodeSessionProcess.getInputStream()));
		BufferedWriter hostSessionWriter = new BufferedWriter(new OutputStreamWriter(this.auditingHubToNodeSessionProcess.getOutputStream()));

		System.out.println("Session created. Sending prompt to admin...");
		this.promptString = String.format("[%s@%s]>", this.adminUserName,this.remoteHost);


		//The command must be put in "". Otherwise, the console will fail to run the echo. Also, the "" must be escaped.
		hostSessionWriter.write("echo " + "\"" + this.promptString + "\"");
		hostSessionWriter.newLine();
		hostSessionWriter.flush();

		StringBuilder hostCompleteResponseBuilder = new StringBuilder();
		String hostInput = null;
		String hostOutput = null;


		while (true){
			try {
				hostOutput = hostSessionReader.readLine();
				hostCompleteResponseBuilder.append(hostOutput);
				adminSessionWriter.write(hostOutput);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();

				if(!hostOutput.equals(this.promptString))
					continue;
				
			} catch (IOException e) {
				System.err.println("Failed to bridge Host->Admin:" + e.getMessage());
				continue; //TODO:Think this through
			}


			this.logger.log(Level.ALL, "Host->Admin:\n"+hostCompleteResponseBuilder.toString());
			hostCompleteResponseBuilder.setLength(0);

			try {
				hostInput = adminSessionReader.readLine();
				hostSessionWriter.write(hostInput+"; echo " + "\"" + this.promptString + "\"");
				hostSessionWriter.newLine();
				hostSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to bridge Admin->Host:" + e.getMessage());
				continue;
			}


			this.logger.log(Level.ALL, "Admin->Host:"+hostInput);


		}

	}

	@Override
	public void run() {

		BufferedReader adminSessionReader;
		BufferedWriter adminSessionWriter;
		try {
			adminSessionReader = new BufferedReader(new InputStreamReader(adminToHubSocket.getInputStream()));
			adminSessionWriter = new BufferedWriter(new OutputStreamWriter(adminToHubSocket.getOutputStream()));
		} catch (IOException e) {
			System.err.println("Error obtaining admin session streams:" + e.getMessage());
			return;
		}

		String adminRequest;
		try {
			adminRequest = adminSessionReader.readLine();
		} catch (IOException e) {
			System.err.println("Error reading synchronization string:" + e.getMessage());
			return;
		} 

		String[] splittedRequest = adminRequest.split(" ");

		if(splittedRequest.equals(Messages.MANAGE)){
			if(!this.auditingHubInstance.checkPermissionAndUnqueue(this.remoteHost)){
				this.adminUserName = splittedRequest[1];
				this.remoteHost = splittedRequest[2];
				try {
					launchManagementSession();
				} catch (IOException e) {
					System.err.println("Failed to launch management session:" + e.getMessage());
				}
				return;
			}
		}

		try {
			adminSessionWriter.write(Messages.ERROR);
			adminSessionWriter.newLine();
			adminSessionWriter.flush();
		} catch (IOException e) {
			System.err.println("Failed to signal failed session start to admin:" + e.getMessage());
		}

	}


}
