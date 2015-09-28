package monitor;

import java.util.ArrayList;
import java.util.List;

public class Minion {
	
	private String ipAddress;
	private List<Application> minionApplications;

	public Minion(String ipAddress){
		this.ipAddress = ipAddress;
		this.minionApplications = new ArrayList<Application>();
		
	}
	
	public String getIpAddress(){
		return ipAddress;
	}
	
	public void addApp(Application app){
		this.minionApplications.add(app);
	}

}
