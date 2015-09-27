package auditinghub;


import java.net.ServerSocket;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Map;
import java.io.IOException;
import global.Ports;

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

		System.out.println("Started Audting Hub");

		AuditingHub auditingHub = new AuditingHub();

		ServerSocket serverSocket = new ServerSocket(Ports.HUB_LOCAL_PORT);
		Socket newSessionSocket;
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
