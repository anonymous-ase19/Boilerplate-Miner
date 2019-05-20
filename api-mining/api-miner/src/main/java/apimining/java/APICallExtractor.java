package apimining.java;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.LinkedListMultimap;

import apimining.pam.main.PAM.LogLevelConverter;
import apimining.pam.main.PAM.Parameters;
import apimining.pam.util.Logging;

import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Extract API calls into ARF Format. Attributes are fqCaller and fqCalls as
 * space separated string of API calls.
 *
 * @author Jaroslav Fowkes <jaroslav.fowkes@ed.ac.uk>
 */
public class APICallExtractor {
	
	/** Main function parameters */
	public static class Parameters {

		@Parameter(names = { "-lf", "--libFolder" }, description = "Source Folder")
		String libFolder = "/ssd1/dayen/APIList/data/source/";
		
		@Parameter(names = { "-nf", "--namespaceFolder" }, description = "Namespace Folder")
		String namespaceFolder = "/ssd1/dayen/APIList/data/namespaces/";

		@Parameter(names = { "-ia", "--interestingAPIs" }, description = "Interesting APIs")
		ArrayList<String> interestingAPIs = new ArrayList<String>();

		@Parameter(names = { "-pf", "--projectFolders" }, description = "Project Folders")
		ArrayList<String> projFolders = new ArrayList<String>();

		@Parameter(names = { "-pn", "--packageNames" }, description = "Package Names")
		ArrayList<String> packageNames = new ArrayList<String>();

		@Parameter(names = { "-of", "--outFolder" }, description = "Output Folder")
		String outFolder = "/ssd1/dayen/APIList/data/calls/";
		
		@Parameter(names = { "-sn", "--sampleNumber"}, description = "Number of Samples")
		Integer numSample = -1;


	}

	// private static final String libFolder = "/disk/data2/jfowkes/example_dataset/java_libraries/";
//	private static final String libFolder = "/Users/dayen/eclipse-workspace/api-mining-master/datasets/source/";
//	private static final String libFolder = "/ssd1/dayen/APIList/data/source/";
	// private static final String libFolder =
	// "/disk/data2/jfowkes/example_dataset/test_train_split/train/";
	// private static final String namespaceFolder = "/disk/data2/jfowkes/example_dataset/namespaces/";
//	private static final String namespaceFolder = "/Users/dayen/eclipse-workspace/api-mining-master/datasets/source/namespaces/";
//	private static final String namespaceFolder = "/ssd1/dayen/APIList/data/namespaces/";
//	private static final String[] interestingAPIs = new String[] {"javax.xml.transform.TransformerFactory"};
//	private static final String[] interestingAPIs = new String[] {};

//	private static final String[] projFolders = new String[] {"javax_xml_transform"};
//	private static final String[] packageNames = new String[] {"javax.xml.transform"};
//	private static final String[] projFolders = new String[] {"io_netty", "org_apache_wicket", "org_neo4j", "soot"};
//	private static final String[] packageNames = new String[] {"io.netty", "org.apache.wicket", "org.neo4j", "soot"};

	// private static final String[] projFolders = new String[] { "netty", "hadoop", "twitter4j", "mahout", "neo4j",
	// 		"drools", "andengine", "spring-data-neo4j", "camel", "weld", "resteasy", "webobjects", "wicket",
	// 		"restlet-framework-java", "cloud9", "hornetq", "spring-data-mongodb" };
	// private static final String[] packageNames = new String[] { "io.netty", "org.apache.hadoop", "twitter4j",
	// 		"org.apache.mahout", "org.neo4j", "org.drools", "org.andengine", "org.springframework.data.neo4j",
	// 		"org.apache.camel", "org.jboss.weld", "org.jboss.resteasy", "com.webobjects", "org.apache.wicket",
	// 		"org.restlet", "edu.umd.cloud9", "org.hornetq", "org.springframework.data.mongodb" };

//	private static final String outFolder = "/ssd1/dayen/APIList/data/calls/";
//	private static final String outFolder = "/Users/dayen/eclipse-workspace/api-mining-master/datasets/API/examples/all/calls/";
	// private static final String outFolder = "/afs/inf.ed.ac.uk/user/j/jfowkes/Code/Sequences/Datasets/API/examples/all/calls/";
	// private static final String outFolder =
	// "/afs/inf.ed.ac.uk/user/j/jfowkes/Code/Sequences/Datasets/API/examples/train/calls/";

	public static void main(final String[] args) throws IOException {
		// Runtime parameters
		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		/** dayen: limit the number of threads */
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "8");
		
		try {
			jc.parse(args);

		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}
		
		String[] interestingAPIs = params.interestingAPIs.toArray(new String[0]);
		String[] projFolders = params.projFolders.toArray(new String[0]);
		String[] packageNames = params.packageNames.toArray(new String[0]);
		Integer numSample = params.numSample;
		
		// For each java file in project
		for (int i = 0; i < packageNames.length; i++) {

			System.out.println("===== Processing " + projFolders[i]);

			final PrintWriter out = new PrintWriter(new File(params.outFolder + projFolders[i] + ".arff"), "UTF-8");

			// ARF Header
//			out.println("@relation " + projFolders[i]);
//			out.println();
//			out.println("@attribute fqCaller string");
//			out.println("@attribute fqCalls string");
//			out.println();
//			out.println("@data");
			
	        // CLAMS ARF Header
	        out.println("@relation " + projFolders[i]);
	        out.println();
	        out.println("@attribute callerFile string");
	        out.println("@attribute callerPackage string");
	        out.println("@attribute fqCaller string");
	        out.println("@attribute fqCalls string");
	        out.println();
	        out.println("@data");

			// Get all java files in source folder
			List<File> files = (List<File>) FileUtils.listFiles(new File(params.libFolder + projFolders[i]),
					new String[] { "java" }, true);
			Collections.sort(files);
			
			// Randomly sample some files
			if (numSample != -1) {
				List<File> sampled_files = new ArrayList<File>();
				Random random = new Random();
				for (int si=0; si < numSample; si++) {
					File random_file = files.get(random.nextInt(files.size()));
					sampled_files.add(random_file);
					files.remove(random_file);
				}
				files = sampled_files;
			}

			int count = 0;
			for (final File file : files) {
//				CLAMs version
	            String fileNameWithOutExt = FilenameUtils.removeExtension(file.getName());
				// if (!file.getName().contains("TestSirenNumericRange"))
				// continue;

				System.out.println("\nFile: " + file);

				// Ignore empty files
				if (file.length() == 0)
					continue;

				if (count % 50 == 0)
					System.out.println("At file " + count + " of " + files.size());
				count++;

//				CLAMs version
				CompilationUnit ast = ASTVisitors.getAST(file);
				/** dayen: added interesting API that should be identified */
				final APICallVisitor acv = new APICallVisitor(ast, params.namespaceFolder, interestingAPIs);
//				final APICallVisitor acv = new APICallVisitor(ASTVisitors.getAST(file), namespaceFolder);
				acv.process();
				final LinkedListMultimap<String, String> fqAPICalls = acv.getAPINames(packageNames[i]);
				
//				from CLAMs version
				String callerPackage = "";
				if (ast.getPackage() != null) {
	                callerPackage = ast.getPackage().getName().toString();
	            }

				for (final String fqCaller : fqAPICalls.keySet()) {
	                out.print("'" + fileNameWithOutExt + "',");
	                out.print("'" + callerPackage + "',");
					out.print("'" + fqCaller + "','");
					String prefix = "";
					for (final String fqCall : fqAPICalls.get(fqCaller)) {
						out.print(prefix + fqCall);
						prefix = " ";
					}
					out.println("'");
				}

			}

			out.close();
		}
	}

}
