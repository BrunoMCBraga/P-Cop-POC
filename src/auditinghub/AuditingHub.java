package auditinghub;

import java.lang.ProcessBuilder;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import global.Ports;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.Process;
//TODO:Allow session exit and clean active threads in case of ending or previous failure.
//TODO:Free resources
//TODO:The hub must know if there are ongoing sessions on a node coming from other hubs. Hub agreement or node notification?
public class AuditingHub {

	
	private Map<Long, Thread> temporaryThreads;
	private Map<String, Thread> remoteHostThreadMap;


	public AuditingHub(){
		
		this.temporaryThreads = new Hashtable<Long,Thread>();
		this.remoteHostThreadMap = new Hashtable<String,Thread>();
		
	}
	//Check if there is another session to a given host
	public boolean checkPermissionAndUnqueue(String remoteHost){
		
		Thread sessionThread = this.remoteHostThreadMap.get(remoteHost);
		
		if(sessionThread != null)
			return false;
		else{
			this.remoteHostThreadMap.put(remoteHost, temporaryThreads.remove(Thread.currentThread().getId()));
			return true;
		}
	}
	
	public static void main(String[] args) throws IOException, InterruptedException{

		AuditingHub auditingHub = new AuditingHub();

		ServerSocket serverSocket = new ServerSocket(Ports.HUB_LOCAL_PORT);
		Socket newSessionSocket;
		AdminSession newSession;
		Thread sessionThread;

		while(true){
			newSessionSocket = serverSocket.accept();
			System.out.println("Connection from:" + newSessionSocket.getRemoteSocketAddress());
			sessionThread = new Thread(new AdminSession(auditingHub, newSessionSocket));
			auditingHub.temporaryThreads.put(sessionThread.getId(), sessionThread);
			sessionThread.start();
		}

	}

}
