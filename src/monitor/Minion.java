package monitor;

import java.util.Hashtable;
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
	
	public Map<String, Application>  getApplications() {
		return this.minionApplications;
	}
	
	public void addApp(Application app){
		this.minionApplications.put(app.getAppId(),app);
	}
	
	public void removeApp(String appId){
		this.minionApplications.remove(appId);
	}
	
	@Override
	public boolean equals(Object obj){
		if (!(obj instanceof Minion))
            return false;
        if (obj == this)
            return true;

        Minion minion = (Minion) obj;
        return this.ipAddress.equals(minion);
	}
	

}
