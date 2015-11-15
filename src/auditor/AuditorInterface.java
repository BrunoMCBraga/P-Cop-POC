package auditor;
import java.lang.ProcessBuilder;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

import exceptions.FailedAttestation;
import exceptions.InvalidMessageException;
import global.AttestationConstants;
import global.Credentials;
import global.Messages;
import global.Ports;
import global.ProcessBinaries;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.Process;

/*
 * Usage:
 * AuditorInterface -h: help
 * AuditorInterface
 * 
 * 
 * */
//TODO:detect broken connections
public class AuditorInterface {

	private static final String AUDITOR_STORE_NAME = "Auditor.jks";
	private static final String MONITORS_TRUST_STORE_NAME = "TrustedMonitors.jks";
	private static final String HUBS_TRUST_STORE_NAME = "TrustedHubs.jks";
	private byte[] monitorSignature;
	private byte[] hubSignature;
	private byte[] minionSignature;
	
	//Attest node against the expected value and uploads configuration signatures for monitor.
	private void attestMonitor(String host) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException, InvalidMessageException, FailedAttestation, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, SignatureException {

			String attestRequestString = String.format("%s %s", Messages.ATTEST,AttestationConstants.NONCE);

			//Keystore initialization
			KeyStore ks = KeyStore.getInstance("JKS");
			FileInputStream keyStoreIStream = new FileInputStream(this.AUDITOR_STORE_NAME);
			ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		

			//KeyManagerFactory initialization
			KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			kmf.init(ks, Credentials.KEY_PASS.toCharArray());

			//TrustStore initialization
			KeyStore ts = KeyStore.getInstance("JKS");
			FileInputStream trustStoreIStream = new FileInputStream(AuditorInterface.MONITORS_TRUST_STORE_NAME);
			ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

			//TrustManagerFactory initialization
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(ts);

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			SSLSocketFactory ssf = context.getSocketFactory();

			Socket monitorSocket =  ssf.createSocket(host, Ports.MONITOR_AUDITOR_PORT);
			BufferedReader attestationSessionReader = new BufferedReader(new InputStreamReader(monitorSocket.getInputStream()));
			BufferedWriter attestationSessionWriter = new BufferedWriter(new OutputStreamWriter(monitorSocket.getOutputStream()));

			attestationSessionWriter.write(attestRequestString);
			attestationSessionWriter.newLine();
			attestationSessionWriter.flush();

			byte[] tpmPubKeyBytes = Base64.getDecoder().decode(AttestationConstants.TPM_PUB_KEY.getBytes());
			X509EncodedKeySpec spec = new X509EncodedKeySpec(tpmPubKeyBytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			PublicKey tpmPubKey = keyFactory.generatePublic(spec);

			
			//This detects when the socket is closed....If null response....
			String quote = attestationSessionReader.readLine();
			String[] splittedQuote = quote.split(" ");
			
			//Cipher cipher = Cipher.getInstance("RSA");   
		    //cipher.init(Cipher.DECRYPT_MODE, tpmPubKey );  
		    //String decryptedQuote = DatatypeConverter.printHexBinary(cipher.doFinal(splittedQuote[1].getBytes()));

			Signature tpmSignature = Signature.getInstance("SHA1withRSA"); 
			tpmSignature.initVerify(tpmPubKey);
			tpmSignature.update(AttestationConstants.DECRYPTED_QUOTE.getBytes());
			
			
			//QUOTE QUOTE TRUSTED_QUOTE
			if(splittedQuote[0].equals(Messages.QUOTE)){
				if(tpmSignature.verify(DatatypeConverter.parseHexBinary(splittedQuote[1]))){
					//OK monitor_singed_config minions_signed_config
					attestationSessionWriter.write(String.format("%s %s %s %s %s",Messages.OK, AttestationConstants.PCR_SHA1,DatatypeConverter.printHexBinary(this.monitorSignature), AttestationConstants.PCR_SHA1,DatatypeConverter.printHexBinary(this.minionSignature)));
					attestationSessionWriter.newLine();
					attestationSessionWriter.flush();
					//send aproved signature.
					return;
				}
			}
			else throw new InvalidMessageException("Expected:" + Messages.QUOTE + ".Received:" + splittedQuote[1]);

			attestationSessionWriter.write(Messages.ERROR);
			attestationSessionWriter.newLine();
			attestationSessionWriter.flush();
			throw new FailedAttestation("Monitor has wrong config");

		}
	//Attest node against the expected value and uploads configuration signatures for logger.
	private void attestLogger(String host) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, KeyManagementException, InvalidMessageException, FailedAttestation, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, SignatureException {

		String attestRequestString = String.format("%s %s", Messages.ATTEST,AttestationConstants.NONCE);

		//Keystore initialization
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream keyStoreIStream = new FileInputStream(this.AUDITOR_STORE_NAME);
		ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//KeyManagerFactory initialization
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
		kmf.init(ks, Credentials.KEY_PASS.toCharArray());

		//TrustStore initialization
		KeyStore ts = KeyStore.getInstance("JKS");
		FileInputStream trustStoreIStream = new FileInputStream(AuditorInterface.HUBS_TRUST_STORE_NAME);
		ts.load(trustStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());

		//TrustManagerFactory initialization
		TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		tmf.init(ts);

		SSLContext context = SSLContext.getInstance("TLS");
		context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

		SSLSocketFactory ssf = context.getSocketFactory();

		Socket loggerSocket =  ssf.createSocket(host, Ports.HUB_AUDITOR_PORT);
		BufferedReader attestationSessionReader = new BufferedReader(new InputStreamReader(loggerSocket.getInputStream()));
		BufferedWriter attestationSessionWriter = new BufferedWriter(new OutputStreamWriter(loggerSocket.getOutputStream()));

		attestationSessionWriter.write(attestRequestString);
		attestationSessionWriter.newLine();
		attestationSessionWriter.flush();
		
		byte[] tpmPubKeyBytes = Base64.getDecoder().decode(AttestationConstants.TPM_PUB_KEY.getBytes());
		X509EncodedKeySpec spec = new X509EncodedKeySpec(tpmPubKeyBytes);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		PublicKey tpmPubKey = keyFactory.generatePublic(spec);

		//This detects when the socket is closed....If null response....
		String quote = attestationSessionReader.readLine();
		String[] splittedQuote = quote.split(" ");

		//Cipher cipher = Cipher.getInstance("RSA");   
	    //cipher.init(Cipher.DECRYPT_MODE, tpmPubKey );  
	    //String decryptedQuote = DatatypeConverter.printHexBinary(cipher.doFinal(splittedQuote[1].getBytes()));

		
		Signature tpmSignature = Signature.getInstance("SHA1withRSA"); 
		tpmSignature.initVerify(tpmPubKey);
		tpmSignature.update(AttestationConstants.DECRYPTED_QUOTE.getBytes());
		
		//QUOTE QUOTE TRUSTED_QUOTE
		if(splittedQuote[0].equals(Messages.QUOTE)){
			if(tpmSignature.verify(DatatypeConverter.parseHexBinary(splittedQuote[1]))){
				//OK monitor_singed_config minions_signed_config
				attestationSessionWriter.write(String.format("%s %s %s",Messages.OK, AttestationConstants.PCR_SHA1,DatatypeConverter.printHexBinary(this.hubSignature)));
				attestationSessionWriter.newLine();
				attestationSessionWriter.flush();
				//send aproved signature.
				return;
			}
		}
		else throw new InvalidMessageException("Expected:" + Messages.QUOTE + ".Received:" + splittedQuote[1]);

		attestationSessionWriter.write(Messages.ERROR);
		attestationSessionWriter.newLine();
		attestationSessionWriter.flush();
		throw new FailedAttestation("Logger has wrong config.");

	}

	private byte[] generateSignature(byte[] data) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableKeyException, NoSuchProviderException, InvalidKeyException, SignatureException{
		//Keystore initialization
		KeyStore ks = KeyStore.getInstance("JKS");
		FileInputStream keyStoreIStream = new FileInputStream(this.AUDITOR_STORE_NAME);
		ks.load(keyStoreIStream, Credentials.KEYSTORE_PASS.toCharArray());
		
		PrivateKey auditorKey = (PrivateKey) ks.getKey("Auditor", Credentials.KEY_PASS.toCharArray());
		keyStoreIStream.close();
		
		Signature rsa = Signature.getInstance("SHA1withRSA"); 
		rsa.initSign(auditorKey);
		rsa.update(data);
		return rsa.sign();
		
		
	}
	
	
	
	public static void main(String[] args) throws IOException{

		AuditorInterface aI = new AuditorInterface();
		try {
			aI.monitorSignature = aI.generateSignature(AttestationConstants.PCR_SHA1.getBytes());
			aI.hubSignature = aI.generateSignature(AttestationConstants.PCR_SHA1.getBytes());
			aI.minionSignature = aI.generateSignature(AttestationConstants.PCR_SHA1.getBytes());
		} catch (UnrecoverableKeyException | InvalidKeyException | KeyStoreException | NoSuchAlgorithmException
				| CertificateException | NoSuchProviderException | SignatureException e1) {
			System.err.println("Failed to generate quote signatures:" + e1.getMessage());
			System.exit(1);
		}

		/*System.out.println(aI.monitorSignature.length);
		String stringedMonitor = new String(aI.monitorSignature, "UTF-8");
		String hexedMonitor = DatatypeConverter.printHexBinary(aI.monitorSignature);
		String recoveredString = new String(DatatypeConverter.parseHexBinary(hexedMonitor), "UTF-8");
		System.out.println(DatatypeConverter.parseHexBinary(hexedMonitor).length);

		if(stringedMonitor.equals(recoveredString))
			System.out.println("Recovered!");
		*/
		System.out.println("Monitor signature:" + DatatypeConverter.printHexBinary(aI.monitorSignature));
		System.out.println("Minion signature:" + DatatypeConverter.printHexBinary(aI.minionSignature));
		System.out.println("Hub signature:" + DatatypeConverter.printHexBinary(aI.hubSignature));


		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));



		while (true){
			System.out.println("Available commands:");
			System.out.println("Attest monitor:attm monitor_host");
			System.out.println("Attest logger:attl logger_host");
			System.out.println("Exit:e");
			System.out.print(">");

			String auditorCommand = promptReader.readLine();
			String[] splittedCommand = auditorCommand.split(" ");
			switch (splittedCommand[0]) {
			case "attm":
				try {
					aI.attestMonitor(splittedCommand[1]);
				} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException
						| NoSuchAlgorithmException | CertificateException | InvalidMessageException
						| FailedAttestation | InvalidKeyException | NoSuchPaddingException | InvalidKeySpecException | IllegalBlockSizeException | BadPaddingException | SignatureException e) {
					System.err.println("Failed to attest monitor:" + e.getMessage());
				}
				break;
			case "attl":
				try {
					aI.attestLogger(splittedCommand[1]);
				} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException
						| NoSuchAlgorithmException | CertificateException | InvalidMessageException
						| FailedAttestation | InvalidKeyException | InvalidKeySpecException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException | SignatureException e) {
					System.err.println("Failed to attest logger:" + e.getMessage());

				}
				break;
			case "exit":
				System.exit(0);
				break;
			default:
				System.out.println("Invalid command.");
				break;
			}


		}


	}




}
