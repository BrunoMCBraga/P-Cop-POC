package monitor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import exceptions.FailedAttestation;
import exceptions.InvalidMessageException;
import exceptions.RejectedConfiguration;
import exceptions.UnregisteredMinion;
import global.AttestationConstants;
import global.Credentials;
import global.Messages;
import global.Ports;

public class MinionsRequestsHandler implements Runnable {

	private static final String MINIONS_TRUST_STORE = "TrustedMinions.jks";
	private static final String AUDITORS_TRUST_STORE_NAME = "TrustedAuditors.jks";
	private Monitor monitor;
	private String hostName;
	private String monitorStore;

	public MinionsRequestsHandler(Monitor monitor) throws IOException {
		this.monitor = monitor;
		this.hostName = monitor.getHostName();
		this.monitorStore = monitor.getHostName()+".jks";
	}


	private void attestMinion(Socket minionSocket) throws IOException, InvalidMessageException, FailedAttestation, KeyStoreException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, SignatureException, IllegalBlockSizeException, BadPaddingException, InvalidKeySpecException, NoSuchPaddingException {

		byte[] signedConfig = null;
		//While approved configuration is unavaiable, wait.
		while((signedConfig = this.monitor.getApprovedConfiguration()) == null){
			System.out.println("NULL");
		}
		

		//Keystore initialization
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream keyStoreIStream = new FileInputStream(this.AUDITORS_TRUST_STORE_NAME);
		ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		Certificate auditorCert = ks.getCertificate("Auditor");
		keyStoreIStream.close();


		BufferedReader minionAttestationReader = minionAttestationReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
		BufferedWriter minionAttestationWriter = minionAttestationWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

		minionAttestationWriter.write(String.format("%s %s", Messages.ATTEST,AttestationConstants.NONCE));
		minionAttestationWriter.newLine();
		minionAttestationWriter.flush();

		
		String[] splittedResponse = minionAttestationReader.readLine().split(" ");
		
		byte[] tpmPubKeyBytes = Base64.getDecoder().decode(AttestationConstants.TPM_PUB_KEY.getBytes());
		X509EncodedKeySpec spec = new X509EncodedKeySpec(tpmPubKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey tpmPubKey = keyFactory.generatePublic(spec);
		
		Cipher cipher = Cipher.getInstance("RSA");   
	    cipher.init(Cipher.DECRYPT_MODE, tpmPubKey );  
	    String decryptedQuote = DatatypeConverter.printHexBinary(cipher.doFinal(DatatypeConverter.parseHexBinary(splittedResponse[1])));

	    
		//Signature tpmSignature = Signature.getInstance("SHA1withRSA"); 
		//tpmSignature.initVerify(tpmPubKey);
		//System.out.println(this.monitor.getMinionsSHA1());
		//tpmSignature.update(DatatypeConverter.parseHexBinary(AttestationConstants.NONCE+this.monitor.getMinionsSHA1()));
				
		//??isto eta errado. e o if de baixo tmb..getClass()..getClass().
		
		System.out.println(String.format("|%s|==|%s|", splittedResponse[0],Messages.QUOTE));
		
		if(splittedResponse[0].equals(Messages.QUOTE)){
			if(decryptedQuote.equals(AttestationConstants.NONCE+this.monitor.getMinionsSHA1())){
				minionAttestationWriter.write(Messages.OK);
				minionAttestationWriter.newLine();
				minionAttestationWriter.flush();
				return;
			}
		}
		else 
				throw new InvalidMessageException("Expected:" + Messages.QUOTE + ".Received:" + splittedResponse[0] + ".");

		minionAttestationWriter.write(Messages.ERROR);
		minionAttestationWriter.newLine();
		minionAttestationWriter.flush();
		throw new FailedAttestation("Minion has config:" + splittedResponse[1] + ". Expected" + AttestationConstants.QUOTE + ".");


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
			FileInputStream trustStoreIStream = new FileInputStream(MinionsRequestsHandler.MINIONS_TRUST_STORE);
			ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//TrustManagerFactory initialization
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e1) {
			System.err.println("Unable to create SSL server context for minions:" + e1.getMessage());
			System.exit(0);
		}

		SSLServerSocketFactory ssf = context.getServerSocketFactory();
		ServerSocket minionsServerSocket = null;
		Socket minionSocket = null;

		try {
			minionsServerSocket = ssf.createServerSocket(Ports.MONITOR_MINION_PORT);
		} catch (IOException e) {
			System.err.println("Failed to create server socket for minions:" + e.getMessage());
			System.exit(1);
		}

		while(true){

			try {
				minionSocket = minionsServerSocket.accept();
			} catch (IOException e) {
				System.err.println("Error while accepting minion connection:" + e.getMessage());
				continue;
			}

			try {
				attestMinion(minionSocket);
			} catch (IOException | InvalidMessageException | FailedAttestation | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException | SignatureException | IllegalBlockSizeException | BadPaddingException | InvalidKeySpecException | NoSuchPaddingException e1) {
				System.err.println("Failed to attest minion:" + e1.getMessage());
				continue;
			}

			BufferedReader socketReader = null; 
			BufferedWriter socketWriter = null;
			try {
				socketReader = new BufferedReader(new InputStreamReader(minionSocket.getInputStream()));
				socketWriter = new BufferedWriter(new OutputStreamWriter(minionSocket.getOutputStream()));

			} catch (IOException e) {
				System.err.println("Error while retrieving minion streams:" + e.getMessage());
				continue;
			}

			String minionRequest = null;

			try {
				minionRequest = socketReader.readLine();
			} catch (IOException e) {
				System.err.println("Error while retrieving minion sync message:" + e.getMessage());
				continue;
			}

			String[] splittedRequest = minionRequest.split(" ");
			boolean requestResult = true;

			switch(splittedRequest[0]){
			case Messages.REGISTER:
				System.out.println("Registering:" + minionSocket.getInetAddress().getHostAddress());
				monitor.addNewMinion(minionSocket.getInetAddress().getHostAddress());
				break;

			}


			if(requestResult){
				System.out.println("Success");
				try {
					socketWriter.write(Messages.OK);
					socketWriter.newLine();
					socketWriter.flush();
				} catch (IOException e) {
					System.err.println("Failed to send OK:" + e.getMessage());
				}
			}
			else{
				System.out.println("Failed");

				try {
					socketWriter.write(Messages.ERROR);
					socketWriter.newLine();
					socketWriter.flush();
				} catch (IOException e) {
					System.err.println("Failed to send ERROR:" + e.getMessage());
				}
			}
		}		
	}

}
