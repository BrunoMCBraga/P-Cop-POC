package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import exceptions.FailedAttestation;
import exceptions.InvalidMessageException;
import exceptions.RejectedConfiguration;
import global.AttestationConstants;
import global.Credentials;
import global.Messages;
import global.Ports;

public class AuditorsRequestsHandler implements Runnable {


	private static final String AUDITORS_TRUST_STORE = "TrustedAuditors.jks";
	private Monitor monitor;
	private String monitorStore;

	public AuditorsRequestsHandler(Monitor monitor) {
		this.monitor = monitor;
		this.monitorStore = monitor.getHostName()+".jks";

	}

	private void processAttestation(Socket auditorSocket,String nonce) throws IOException, InvalidMessageException, RejectedConfiguration {

		BufferedReader attestationReader = attestationReader = new BufferedReader(new InputStreamReader(auditorSocket.getInputStream()));
		BufferedWriter attestationWriter = attestationWriter = new BufferedWriter(new OutputStreamWriter(auditorSocket.getOutputStream()));

		attestationWriter.write(String.format("%s %s", Messages.QUOTE, AttestationConstants.QUOTE));
		attestationWriter.newLine();
		attestationWriter.flush();



		if(attestationReader.readLine().equals(Messages.ERROR))
			throw new RejectedConfiguration("Auditor rejected platform attestation.");

	}

	@Override
	public void run() {



		SSLContext context = null;
		try {
			//Keystore initialization
			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream keyStoreIStream = new FileInputStream(this.monitorStore);
			ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//KeyManagerFactory initialization
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, Credentials.KEY_PASS.toCharArray());

			//TrustStore initialization
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream trustStoreIStream = new FileInputStream(AuditorsRequestsHandler.AUDITORS_TRUST_STORE);
			ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//TrustManagerFactory initialization
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for auditors:" + e1.getMessage());
			System.exit(0);
		}

		SSLServerSocketFactory ssf = context.getServerSocketFactory();
		ServerSocket auditorsServerSocket = null;
		Socket auditorSocket = null;

		try {
			auditorsServerSocket = ssf.createServerSocket(Ports.MONITOR_AUDITOR_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for auditors:" + e.getMessage());
			System.exit(1);
		}

		while(true){

			try {
				auditorSocket = auditorsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting auditor connection:" + e.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(auditorSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(auditorSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving auditor streams:" + e.getMessage());
				continue;
			}

			String auditorRequest = null;

			try {
				auditorRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving auditor sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = auditorRequest.split(" ");

			switch(splittedRequest[0]){
			case Messages.ATTEST:
				System.out.println("Processing attestation...");
				try {
					processAttestation(auditorSocket,splittedRequest[1]);
				} catch (IOException | InvalidMessageException | RejectedConfiguration e) {
					System.err.println("Failed to be attested by auditor:" + e.getMessage());
					System.exit(1);
				}
				break;

			}

		}		
	}


}
