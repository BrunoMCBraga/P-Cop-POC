package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import global.Messages;
import monitor.Minion;

/*
 *-Receives deployment requests: Deploy App_Folder 
 *-Picks code and interacts with docker to create container
 *-
 * */
//TODO:Turn this into a task if necessary
public class MonitorRequestsHandler implements Runnable {

	private ServerSocket monitorServerSocket;
	
	public MonitorRequestsHandler(ServerSocket monitorServerSocket) {
		this.monitorServerSocket = monitorServerSocket;
	}
	
	private boolean deployApp(String appDir) throws IOException, InterruptedException{
		
		String dockerAppName=appDir.toLowerCase();
		
		String createContainerCommand = String.format("sudo docker build -t %s-container ../../%s",dockerAppName,appDir);
		String deployContainerCommand = String.format("sudo docker run -it -p 80 --rm --name %s %s-container",dockerAppName,dockerAppName);
		
		
		String[]  createContainerCommandArray = createContainerCommand.split(" ");
		String[]  deployContainerCommandArray = deployContainerCommand.split(" ");

		
		ProcessBuilder createConainerProcessBuilder = new ProcessBuilder(createContainerCommandArray);
		Process createContainerProcess = createConainerProcessBuilder.start();
		int createContainerResult = createContainerProcess.waitFor();
		if (createContainerResult != 0){
			//Not sure why but the process runs but returns an abnormal value...
			System.out.println("Create container process returned with abnormal value (" + createContainerResult +") . Try again later.");
			//System.exit(1);
		}
		ProcessBuilder deployConainerProcessBuilder = new ProcessBuilder(deployContainerCommandArray);
		Process deployProcess = deployConainerProcessBuilder.start();
		//int deployContainerResult = deployProcess.waitFor();
		//if (deployContainerResult != 0){
			//Not sure why but the process runs but returns an abnormal value...
			//System.out.println("Deploy container process returned with abnormal value (" + deployContainerResult +") . Try again later.");
			//System.exit(1);
		//}
		return createContainerResult==0?true:false;
	}
	
	private void processMonitorRequest(Socket monitorSocket) throws IOException{
		BufferedReader socketReader = null; 
		BufferedWriter socketWriter = null;
		
		
		socketReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		socketWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		String monitorRequest= socketReader.readLine();
		String[] splittedRequest = monitorRequest.split(" ");
		boolean deployResult = false;
		if(splittedRequest[0].equals(Messages.DEPLOY)){
			System.out.println("Deploying:" + splittedRequest[1]);
			try {
				deployResult = deployApp(splittedRequest[1]);
			} catch (InterruptedException e) {
				System.err.println("Unable to deploy:"+splittedRequest[1]);
			}
		}
		
		if(deployResult)
			System.out.println("Deployed successfully");
		socketWriter.write(Messages.OK);
		socketWriter.newLine();
		socketWriter.flush();
	}
	
	
	@Override
	public void run() {
		
		Socket monitorSocket = null;
		
		while(true){
			
			try {
				monitorSocket = monitorServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting master connection:" + e.getMessage());
				try {
					monitorSocket.close();
					monitorServerSocket.close();
				} catch (IOException e1) {
				}
				System.exit(1);
			}
			try {
				processMonitorRequest(monitorSocket);
				monitorSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing monitor request:"+e.getMessage());
				System.exit(1);

			}
			
		}
		
	}
	
	
	
	
}
