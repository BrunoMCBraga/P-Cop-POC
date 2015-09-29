package minion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.UnknownHostException;

import auditinghub.AdminSession;
import developer.DeveloperInterface;
import exceptions.InvalidMessageException;
import global.Messages;
import global.Ports;

/*
 * NodeGuard -m monitorHostName
 * In terms of application deployment, files will not be transmitted through this channel but using SSH.This is just a control channel
 * How it word:
 * 1.Nodes contact master so it learns of their address
 * 2.connection closes
 * 3.Guard listens for control messages from the monitor or logger.
 * 
 * Monitor messages are: monitor:.....\n while logger are logger:.....\n
 * */

public class NodeGuard {

	private static final int MONITOR_HOST_FLAG_INDEX=0;
	private String monitorHost;

	public NodeGuard(String monitorHost){
		this.monitorHost = monitorHost;
	}

	private boolean startMonitorHandler() throws UnknownHostException, IOException, InvalidMessageException {

		Socket monitorSocket = new Socket(this.monitorHost,Ports.MONITOR_MINION_PORT);

		BufferedReader monitorSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
		BufferedWriter monitorSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));
		
		monitorSessionWriter.write(Messages.REGISTER);
		monitorSessionWriter.newLine();
		monitorSessionWriter.flush();



		String monitorResponse = monitorSessionReader.readLine();
		
		if(monitorResponse.equals(Messages.OK)){
			new Thread(new MonitorRequestsHandler()).start();
			return true;
		}
		else if(monitorResponse.equals(Messages.ERROR))
			return false;
		else throw new InvalidMessageException("Invalide response:" + monitorResponse);
		

	}


	private boolean startHubHandler() throws IOException {
		new Thread(new HubRequestsHandler()).start();
		return true;
	}


	public static void main(String[] args){

		System.out.println("Started Node Guard");

		String monitorHost;
		NodeGuard nodeGuard;
		boolean handlersStartResult = true;

		switch(args.length){
		case 2:
			monitorHost = args[MONITOR_HOST_FLAG_INDEX+1];
			nodeGuard = new NodeGuard(monitorHost);
			
			try {
				handlersStartResult &= nodeGuard.startMonitorHandler();
				handlersStartResult &= nodeGuard.startHubHandler();
			} catch (IOException | InvalidMessageException e) {
				System.err.println("Failed to begin server processes:" + e.getMessage());
				System.exit(1);
			}
			break;
		default:
			System.err.println("NodeGuard -m monitorHostName");
			System.exit(0);
			break;

		}

		if(handlersStartResult)
			try {
				Thread.currentThread().wait();
			} catch (InterruptedException e) {
				System.exit(0);
			}

		
	}

}
