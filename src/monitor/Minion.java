package monitor;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class Minion {
	
	private String ipAddress;
	private Map<String, Application> minionApplications;

	public Minion(String ipAddress){
		this.ipAddress = ipAddress;
		this.minionApplications = new Hashtable<String,Application>();
		
	}
	
	public String getIpAddress(){
		return ipAddress;
	}
	
	public void addApp(Application app){
		this.minionApplications.put(app.getAppId(),app);
	}
	
	public void removeApp(String appId){
		this.minionApplications.remove(appId);
	}
	
	

}
