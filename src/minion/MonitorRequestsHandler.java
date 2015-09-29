package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import global.Directories;
import global.Messages;
import global.Ports;
import monitor.Minion;

/*
 *-Receives deployment requests: Deploy App_Folder 
 *-Picks code and interacts with docker to create container
 *-
 * */
//TODO:Turn this into a task if necessary
//TODO:Thread pools
public class MonitorRequestsHandler implements Runnable {

	private ServerSocket monitorServerSocket;

	public MonitorRequestsHandler() throws IOException {
		this.monitorServerSocket = new ServerSocket(Ports.MINION_MONITOR_PORT);;
	}

	private boolean deployApp(String appId) throws IOException, InterruptedException{

		String createContainerCommand = String.format("sudo docker build -t %s-container ../../%s%s",appId,Directories.APPS_DIR,appId);
		String deployContainerCommand = String.format("sudo docker run -p 80 -d --name %s %s-container",appId,appId);


		String[]  createContainerCommandArray = createContainerCommand.split(" ");
		String[]  deployContainerCommandArray = deployContainerCommand.split(" ");


		ProcessBuilder createConainerProcessBuilder = new ProcessBuilder(createContainerCommandArray);
		Process createContainerProcess = createConainerProcessBuilder.start();
		int processResult = createContainerProcess.waitFor();
		if (processResult != 0){
			return false;
		}
		ProcessBuilder deployConainerProcessBuilder = new ProcessBuilder(deployContainerCommandArray);
		Process deployProcess = deployConainerProcessBuilder.start();
		processResult += deployProcess.waitFor();

		return processResult==0?true:false;
	}

	private boolean deleteApp(String appId) throws IOException, InterruptedException {

		String deleteAppCommand = String.format("sudo ../DeleteApp.sh " + appId);
		String[]  deleteAppCommandArray = deleteAppCommand.split(" ");


		ProcessBuilder deleteAppProcessBuilder = new ProcessBuilder(deleteAppCommandArray);
		Process deleteAppCommandProcess = deleteAppProcessBuilder.start();

		int deleteContainerResult = deleteAppCommandProcess.waitFor();
		if (deleteContainerResult != 0){
			return false;
		}

		return true;
	}

	@Override
	public void run() {

		Socket monitorSocket = null;

		while(true){

			try {
				monitorSocket = monitorServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting master connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving streams:" + e.getMessage());
				continue;
			}

			String monitorRequest = null;

			try {
				socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = monitorRequest.split(" ");
			boolean requestResult = false;

			switch(splittedRequest[0]){
			case Messages.DEPLOY:
				System.out.println("Deploying:" + splittedRequest[1]);
				try {
					requestResult = deployApp(splittedRequest[1]);
				} catch (InterruptedException | IOException e) {
					System.err.println("Unable to deploy:" + e.getMessage());
				}

				break;

			case Messages.DELETE:
				System.out.println("Deleting:" + splittedRequest[1]);
				try {
					requestResult = deleteApp(splittedRequest[1]);
				} catch (InterruptedException | IOException e) {
					System.err.println("Unable to delete:"+splittedRequest[1]);
				}
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





		/*try {
				processMonitorRequest(monitorSocket);
				monitorSocket.close();
			} catch (IOException e) {
				System.err.println("Error while processing monitor request:"+e.getMessage());
				System.exit(1);

			}*/



	}





}
