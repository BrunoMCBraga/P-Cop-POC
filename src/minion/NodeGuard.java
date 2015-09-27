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
	
	public static void main(String[] args){
		
		System.out.println("Started Node Guard");
		if(args.length != 2){
			System.err.println("NodeGuard -m monitorHostName");
			System.exit(1);
		}
		
		Socket monitorSocket = null;
		try {
			monitorSocket = new Socket(args[1],Ports.MONITOR_MINION_PORT);
		} catch (NumberFormatException|IOException e) {
			System.err.println("Error on socket creation and connection:"+e.getMessage());
			System.exit(1);
		}
		BufferedReader monitorSessionReader = null;
		BufferedWriter monitorSessionWriter = null;
		try {
			monitorSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
			monitorSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

		} catch (IOException e) {
			System.err.println("Error on obtaining reader/writer:"+e.getMessage());
			System.exit(1);
		}
		
		//sending control message
		try {
			monitorSessionWriter.write(Messages.REGISTER);
			monitorSessionWriter.newLine();
			monitorSessionWriter.flush();
		} catch (IOException e) {
			System.err.println("Error on sending hello:"+e.getMessage());
			System.exit(1);
		}

		
		String monitorResponse = null; 
		
		
		try {
			monitorResponse = monitorSessionReader.readLine();
		} catch (IOException e) {
			System.err.println("Error on receiving bye:"+e.getMessage());
			System.exit(1);
		}
		if(!monitorResponse.equals(Messages.OK))
		{
			System.err.println("Error on synchronizing with master. Expected:" + Messages.OK + " Received:"+monitorResponse);
			System.exit(1);
		}
		
		System.out.println("Registered successfully.");
		
		ServerSocket monitorControlServerSocket = null;
		ServerSocket hubControlServerSocket = null;

		try {
			monitorControlServerSocket = new ServerSocket(Ports.MINION_MONITOR_PORT);
			hubControlServerSocket = new ServerSocket(Ports.MINION_HUB_PORT);
		} catch (IOException e) {
			System.err.println("Error on creating server sockets:"+e.getMessage());
			System.exit(1);
		}
		
		new Thread(new MonitorRequestsHandler(monitorControlServerSocket)).start();
		new Thread(new LoggersRequestsHandler(hubControlServerSocket)).start();
		

		while(true){
			
		}
		
		
		
	
	}
	
}
