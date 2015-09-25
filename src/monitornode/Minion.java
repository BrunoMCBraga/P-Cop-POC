package monitornode;

public class Minion {
	
	private String ipAddress;

	public Minion(String ipAddress){
		this.ipAddress = ipAddress;
		
	}
	
	public String getIpAddress(){
		return ipAddress;
	}

}
