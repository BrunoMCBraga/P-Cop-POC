package auditinghub;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

//Auditors responsible for checking the logs may use this interface to commit them. For now commit is just putting them on another folder
//In the future will unlock untrusted nodes.
public class AuditorInterface {

	private static final String UNCOMMITED_LOGS_DIR="Logs/Uncommited/";
	private static final String COMMITED_LOGS_DIR="Logs/Commited/";

	public static void main(String[] args) throws IOException{

		Path commitedLogsDirPath = FileSystems.getDefault().getPath(COMMITED_LOGS_DIR);
		if(Files.notExists(commitedLogsDirPath))
			Files.createDirectories(commitedLogsDirPath);

		Path uncommitedLogsDirPath = FileSystems.getDefault().getPath(UNCOMMITED_LOGS_DIR);

		Stream<Path> uncommitedLogsStream; 
		Stream<Path> commitedLogsStream;

		BufferedReader promptReader= new BufferedReader(new InputStreamReader(System.in));
		BufferedWriter promptWriter= new BufferedWriter(new OutputStreamWriter(System.out));


		while (true){
			System.out.println("Available commands:");
			System.out.println("List uncommited logs:lu");
			System.out.println("List commited logs:lc");
			System.out.println("Read uncommited log:r log_id");
			System.out.println("Commit log:c log_id");
			System.out.println("Exit:e");
			System.out.print(">");

			String auditorCommand = promptReader.readLine();
			String[] splittedCommand = auditorCommand.split(" ");
			switch (splittedCommand[0]) {
			case "lu":
				//what if . instead of ::??
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				uncommitedLogsStream.forEach(P->System.out.println(P.getFileName()));
				break;
			case "lc":
				commitedLogsStream = Files.find(commitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				commitedLogsStream.forEach(P->System.out.println(P.getFileName()));
				break;
			case "r":
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1, (P,A)->P.toString().matches(".*.log"));
				List<String> logLines = Files.readAllLines(uncommitedLogsStream.filter(P->P.getFileName().toString().matches(splittedCommand[1])).findFirst().get());
				logLines.stream().forEach(System.out::println);
				break;

			case "c":
				uncommitedLogsStream = Files.find(uncommitedLogsDirPath, 1,  (P,A)->P.toString().matches(".*.log"));
				Path oldLogPath = uncommitedLogsStream.filter(P->P.getFileName().toString().matches(splittedCommand[1])).findFirst().get();
				Path newLogPath = FileSystems.getDefault().getPath(commitedLogsDirPath.toString(),oldLogPath.getFileName().toString());
				Files.move(oldLogPath,newLogPath);
				break;
			case "e":
				System.exit(0);
			default:
				System.out.println("Invalid command.");
				break;
			}


		}



	}
}