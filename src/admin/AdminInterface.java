package admin;
import java.lang.ProcessBuilder;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import exceptions.FailedAttestation;
import exceptions.InvalidMessageException;
import global.AttestationConstants;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Process;

/*
 * Usage:
 * AdminInterface -h: help
 * AdminInterface -a hub -h host -u username -k key
 * 
 * 
 * */
//TODO:detect broken connections
public class AdminInterface {

	private static final String EXIT_COMMAND="exit";
	private static final int HUB_FLAG_INDEX=0;
	private static final int HOST_FLAG_INDEX=2;
	private static final int USERNAME_FLAG_INDEX=4;
	private static final int ADMIN_KEY_INDEX=6;
	private String userName;
	private String hubHost;
	private String remoteHost;
	private String key;
	private Socket hubSocket;
	private Process proxyProcess;

	public AdminInterface(String userName, String hubHost, String remoteHost, String key){
		this.userName = userName;
		this.hubHost = hubHost;
		this.remoteHost= remoteHost;
		this.key = key;
	}

	private void attestLogger(Socket loggerSocket) throws IOException, FailedAttestation, InvalidMessageException {
		BufferedReader loggerAttestationReader = new BufferedReader(new InputStreamReader(loggerSocket.getInputStream()));
		BufferedWriter loggerAttestationWriter = new BufferedWriter(new OutputStreamWriter(loggerSocket.getOutputStream()));

		//ATEST NONCE
		loggerAttestationWriter.write(String.format("%s %s", Messages.ATTEST,AttestationConstants.NONCE));
		loggerAttestationWriter.newLine();
		loggerAttestationWriter.flush();

		String quote = loggerAttestationReader.readLine();
		String[] splittedMessage = quote.split(" ");
		
		//QUOTE QUOTE TRUSTED_QUOTE
		if(splittedMessage[0].equals(Messages.QUOTE)){
			if(splittedMessage[1].equals(splittedMessage[2])){
				System.out.println("Monitor has trusted configuration.");
				loggerAttestationWriter.write(Messages.OK);
				loggerAttestationWriter.newLine();
				loggerAttestationWriter.flush();
				return;
			}
		}
		else throw new InvalidMessageException("Expected:" + Messages.QUOTE + ". Received:" + splittedMessage[0]);


		loggerAttestationWriter.write(Messages.ERROR);
		loggerAttestationWriter.newLine();
		loggerAttestationWriter.flush();
		throw new FailedAttestation("Logger has config:" + splittedMessage[1] + ". Expected:" + splittedMessage[2]);
	}



	private boolean startLocalProxy() throws IOException, InterruptedException{
		String sshArgs = String.format("-i %s -oStrictHostKeyChecking=no -f %s@%s -L %d:%s:%d -N",this.key, this.userName,this.hubHost,Ports.ADMIN_SSH_PORT,this.hubHost,Ports.HUB_LOCAL_PORT);
		String[] sshArgsArray = sshArgs.split(" ");
		List<String> finalCommand = new ArrayList<String>(sshArgsArray.length+1);
		finalCommand.add(ProcessBinaries.SSH_DIR);

		for (String arg : sshArgsArray)
			finalCommand.add(arg);

		ProcessBuilder sshSessionBuilder = new ProcessBuilder(finalCommand);
		this.proxyProcess = sshSessionBuilder.start();


		int processResult = proxyProcess.waitFor();
		if (processResult != 0){
			return false;
		}

		return true;

	}


	private boolean manageNode() throws IOException, InterruptedException, InvalidMessageException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException, CertificateException, KeyManagementException, FailedAttestation {

		boolean proxyCreationResult = startLocalProxy();


		if(!proxyCreationResult){
			System.err.println("Failed to create local proxy");
			return false;
		}

		String manageRequestString = String.format("%s %s %s", Messages.MANAGE,this.userName,this.remoteHost);
		this.hubSocket =  new Socket((String)null, Ports.ADMIN_SSH_PORT);
	    
		System.out.println("Attesting hub...");
		attestLogger(this.hubSocket);

		
		BufferedReader adminManageSessionReader = new BufferedReader(new InputStreamReader(this.hubSocket.getInputStream()));
		BufferedWriter adminManageSessionWriter = new BufferedWriter(new OutputStreamWriter(this.hubSocket.getOutputStream()));

		adminManageSessionWriter.write(manageRequestString);
		adminManageSessionWriter.newLine();
		adminManageSessionWriter.flush();

		//This detects when the socket is closed....If null response....
		String response = adminManageSessionReader.readLine();
		switch(response){
		case Messages.OK:
			System.out.println("Success on requesting management session.");
			Runtime.getRuntime().addShutdownHook(new Thread(new CTRLCHandler(this.hubSocket,this.proxyProcess)));
			break;
		case Messages.ERROR:
			return false;
		default:
			throw new InvalidMessageException("Invalide response:" + response);
		}

		String prompt = String.format("[%s@%s]>", this.userName,this.remoteHost);
		BufferedReader adminSessionReader = new BufferedReader(new InputStreamReader(this.hubSocket.getInputStream())); 
		BufferedWriter adminSessionWriter = new BufferedWriter(new OutputStreamWriter(this.hubSocket.getOutputStream()));


		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));
		BufferedWriter promptWriter= new BufferedWriter(new OutputStreamWriter(System.out));

		String hostInput = null;
		String hostOutput = null;
		//TODO:Detect ctrl+c
		while (true){
			try {
				hostOutput = adminSessionReader.readLine();
				if(hostOutput.equals(Messages.ERROR))
				{
					System.out.println("Failed to continue session...");
					return false;
				}
				promptWriter.write(hostOutput);
				promptWriter.newLine();
				promptWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to retrieve hub message:" + e.getMessage());
				continue;
			}

			if(!hostOutput.equals(prompt))
				continue;


			try {
				hostInput = promptReader.readLine();
				if(hostInput.equals(EXIT_COMMAND)){
					Thread cleanupThread = new Thread(new CTRLCHandler(this.hubSocket,this.proxyProcess));
					cleanupThread.start();
					cleanupThread.join();
					break;
				
				}
				adminSessionWriter.write(hostInput);
				adminSessionWriter.newLine();
				adminSessionWriter.flush();
			} catch (IOException e) {
				System.err.println("Failed to send command to hub:" + e.getMessage());
				continue;
			}


		}

		System.out.println("Bye!");
		return true;

	}

	public static void main(String[] args){
		
		AdminInterface aI;

		String userName;
		String hubHost;
		String remoteHost;	
		String key;
		boolean commandResult = false;

		switch (args.length){
		case 8:
			userName = args[USERNAME_FLAG_INDEX+1];
			hubHost=args[HUB_FLAG_INDEX+1];
			remoteHost=args[HOST_FLAG_INDEX+1];
			key = args[ADMIN_KEY_INDEX+1];
			aI = new AdminInterface(userName, hubHost, remoteHost, key);
			try {
				commandResult = aI.manageNode();
			} catch (IOException | InterruptedException | InvalidMessageException | UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException | CertificateException | FailedAttestation e) {
				System.err.println("Failed to run management session:" + e.getMessage());
				System.exit(1);
			}
			break;
		default:
			System.out.println("Usage: AdminInterface -a hub -h host -u userName -k key");
			System.exit(0);
			break;
		}

		if(!commandResult){
			System.err.println("Failed to perform request.");
			System.exit(1);
		}
		
		System.exit(0);

	}



}
