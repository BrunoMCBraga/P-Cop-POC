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

//TODO:Detect mistyping errors. Since the session is not interactive, erros seem not to be being sent.
public class AdminSession implements Runnable {

	private static final String HUB_CREDENTIALS_PATH="./";
	private static final String HUB_USERNAME="securepa";
	private static final String SSH_DIR="/usr/bin/ssh";
	private static final String UNCOMMITED_LOGS_DIR="Logs/Uncommited/";

	AuditingHub auditingHubInstance;
	private Socket adminToHubSocket;

	private String adminUserName;
	private String remoteHost;
	private String promptString;
	private Process auditingHubToNodeSessionProcess;
	private Logger logger;

	public AdminSession(AuditingHub auditingHubInstance,Socket adminToHubSocket){

		this.auditingHubInstance = auditingHubInstance;
		this.adminToHubSocket = adminToHubSocket;


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

	private void launchSessionProcess() throws IOException{


		String sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -t %s@%s -vvvvv",HUB_CREDENTIALS_PATH+this.remoteHost, HUB_USERNAME,this.remoteHost);
		System.out.println(sshArgs);
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);

		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);
		this.auditingHubToNodeSessionProcess = sshSessionBuilder.start();


	}

	@Override
	public void run() {

		BufferedReader adminSessionReader;
		BufferedWriter adminSessionWriter;
		try {
			adminSessionReader = new BufferedReader(new InputStreamReader(adminToHubSocket.getInputStream()));
			adminSessionWriter = new BufferedWriter(new OutputStreamWriter(adminToHubSocket.getOutputStream()));
		} catch (IOException e) {
			System.err.println("Error obtaining admin session streams:"+e.getMessage());
			return;
		}

		String userAndHost;
		try {
			userAndHost = adminSessionReader.readLine();
		} catch (IOException e) {
			System.err.println("Error reading synchronization string:"+e.getMessage());
			return;
		} 

		System.out.println("Destination host and username received:" + userAndHost);
		String userAndHostArray[] = userAndHost.split("@"); 
		this.adminUserName=userAndHostArray[0];
		this.remoteHost=userAndHostArray[1];

		if(!this.auditingHubInstance.checkPermissionAndUnqueue(this.remoteHost))
			return;

		try {
			launchSessionProcess();
		} catch (IOException e) {
			System.err.println("Error launching Hub->Node session:"+e.getMessage());
			return;
		}

		try {
			launchLogger();
		} catch (SecurityException|IOException e) {
			System.err.println("Error launching logger:"+e.getMessage());
			return;
		}

		BufferedReader hostSessionReader = new BufferedReader(new InputStreamReader(this.auditingHubToNodeSessionProcess.getInputStream()));
		BufferedWriter hostSessionWriter = new BufferedWriter(new OutputStreamWriter(this.auditingHubToNodeSessionProcess.getOutputStream()));

		System.out.println("Session created. Sending prompt to admin...");
		this.promptString = String.format("[%s]>", userAndHost);


		try {
			//The command must be put in "". Otherwise, the console will fail to run the echo. Also, the "" must be escaped.
			hostSessionWriter.write("echo " + "\"" + this.promptString + "\"");
			hostSessionWriter.newLine();
			hostSessionWriter.flush();
		} catch (IOException e) {
			System.err.println("Error on first prompt:"+e.getMessage());
			return;
		}


		StringBuilder hostCompleteResponseBuilder = new StringBuilder();


		String hostInput = null;
		String hostOutput = null;


		while (true){
			System.out.println("Now waiting for remote node...");
			try {
				while ((hostOutput = hostSessionReader.readLine()) != null){

					System.out.println("Line read:" + hostOutput);
					adminSessionWriter.write(hostOutput);
					adminSessionWriter.newLine();

					if(!hostOutput.equals(this.promptString)){
						hostCompleteResponseBuilder.append(hostOutput+System.lineSeparator());
						continue;
					}
					else{
						break;
					}
				}
				adminSessionWriter.flush();

			} catch (IOException e) {
				System.err.println(e.getMessage());
				return;
			}


			this.logger.log(Level.ALL, "Host->Admin:\n"+hostCompleteResponseBuilder.toString());
			hostCompleteResponseBuilder.setLength(0);

			System.out.println("Now waiting for admin...");
			try {
				hostInput = adminSessionReader.readLine();
				hostSessionWriter.write(hostInput+"; echo " + "\"" + this.promptString + "\"");
				hostSessionWriter.newLine();
				hostSessionWriter.flush();
			} catch (IOException e) {
				System.err.println(e.getMessage());
				return;
			}


			this.logger.log(Level.ALL, "Admin->Host:"+hostInput);


		}

	}
}
