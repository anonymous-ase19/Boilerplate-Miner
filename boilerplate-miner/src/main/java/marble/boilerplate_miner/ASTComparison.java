package marble.boilerplate_miner;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import apimining.pam.main.PAM.LogLevelConverter;
import at.unisalzburg.dbresearch.apted.costmodel.PerEditOperationStringNodeDataCostModel;
import at.unisalzburg.dbresearch.apted.costmodel.StringUnitCostModel;
import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.Node;
import at.unisalzburg.dbresearch.apted.node.StringNodeData;
import at.unisalzburg.dbresearch.apted.parser.BracketStringInputParser;
import at.unisalzburg.dbresearch.apted.parser.InputParser;
import spoon.Launcher;
import spoon.reflect.CtModel;

import com.github.gumtreediff.actions.ActionGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.Generators;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.io.ActionsIoUtils;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.io.TreeIoUtils.TreeSerializer;
import com.github.gumtreediff.io.TreeIoUtils.XmlInternalGenerator;
import com.github.gumtreediff.matchers.Mapping;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.utils.Pair;

public class ASTComparison {
	
	public static class Parameters {

		@Parameter(names = { "-f", "--file" }, description = "Log file with usage patterns and the clicnet code list")
		String logFile = "/Users/dayen/eclipse-workspace/api-mining-master/output/all/javax_xml_transform/pam/PAM_logs.log";
//		String logFile = "/Users/dayen/Documents/Research/API/Mining/serverMining/PAM_logs.log";
		
		/** dayen: add source file directory to print the method implementation of patterns */
		@Parameter(names = { "-sd", "--source" }, description = "Source code directory")
		String sourceDir = "/Users/dayen/eclipse-workspace/api-mining-master/datasets/source/javax_xml_transform/";

		@Parameter(names = { "-o", "--outDIr" }, description = "Output directory")
//		String outFile = "/afs/inf.ed.ac.uk/user/j/jfowkes/Code/Sequences/Datasets/API/examples/all/hadoop/PAM_seqs.txt";
		/** dayen: when printing files containing the patterns, input folder dir instead of the file name */
		String outDir = "/Users/dayen/eclipse-workspace/api-mining-master/output/all/javax_xml_transform/pam/diff/";

		@Parameter(names = { "-pl", "--patternLimit" }, description = "Top n patterns to evaluate")
		int patternLimit = 9;
		
		@Parameter(names = { "-ps", "--patternStart" }, description = "Top n patterns to evaluate")
		int patternStart = 7;
		
		@Parameter(names = { "-pb", "--projectBiasThreshold" }, description = "Pattern is biased if shown less than this number.")
		int biasThreshold = 3;
		
		@Parameter(names = {"-p", "--processor"}, description = "Number of Threads")
		int numThread = 1;
		
		@Parameter(names = {"-ms", "--max_statement"}, description = "Max number of AST method invocation nodes a single-call subtree can have")
		int max_api_calls = 20;
		
	}
	
	public static void main(final String[] args) throws Exception{
		// Runtime parameters
		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);
		
		long tStart = System.currentTimeMillis();

		/** dayen: limit the number of threads */
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(params.numThread));
		
		try {
			jc.parse(args);

			System.out.println("Processing " + FilenameUtils.getBaseName(params.logFile) + "...");
			compareASTs(params.logFile, params.sourceDir, params.outDir, params.patternStart, params.patternLimit, params.numThread, params.biasThreshold, params.max_api_calls);

		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}
		long tEnd = System.currentTimeMillis();
		long tDelta = tEnd - tStart;
		double elapsedSeconds = tDelta / 1000.0;
		System.out.println(elapsedSeconds);
	}
	
	private static void compareASTs(final String logFile, final String sourceDir, final String outDir, 
			final int patternStart, final int patternLimit, final int numThread, final int biasThreshold, final int max_api_calls) throws Exception {
		HashMap<Integer, APIPattern> dictionary = new HashMap<Integer, APIPattern>();
		readLogFile(logFile, dictionary);
		
		Run.initGenerators();

		for (final Entry<Integer, APIPattern> entry : dictionary.entrySet()) {
			if (entry.getKey() < patternStart) {
				continue;
			}
			if (entry.getKey() > patternLimit) {
				break;
			}
			
			final File sf = new File(outDir + "Similarity_" + Integer.toString(entry.getKey()) + ".txt");
			final PrintWriter sOut = new PrintWriter(sf);
			
			final File el = new File(outDir + "pattern_" + Integer.toString(entry.getKey()) + ".edgelist");
			final PrintWriter eOut = new PrintWriter(el);
			
//			final File lf = new File(outDir + "positions_" + Integer.toString(entry.getKey()) + ".txt");
//			final PrintWriter lOut = new PrintWriter(lf);
//			
			final APIPattern apiPattern = entry.getValue();
			final ArrayList<ClientMethod> client_methods = new ArrayList<ClientMethod>();
			sOut.println(apiPattern.getPatternInString());
			sOut.println();
			
			System.out.println();
			apiPattern.printPattern();
			final ArrayList<String> curPattern = apiPattern.getPattern();
			
//			if (curPattern.size() == 1) {
//				continue;
//			}

			
			HashSet<String> fileSet = new HashSet<String>();
			
			for (int i=0; i<apiPattern.getClientMethod().size(); i++) {
				String cur_file = apiPattern.getClientMethod().get(i).getFileName();
				if (fileSet.contains(cur_file)) {
					continue;
				}
				else {
					fileSet.add(cur_file);
					client_methods.add(apiPattern.getClientMethod().get(i));
				}
			}
			
			
			if (client_methods.size() < biasThreshold) {
				System.out.println("This pattern is biased. ");
				continue;
			}
			
//			for (ClientMethod srcMethod : client_methods) {
//				int i = client_methods.indexOf(srcMethod);
//				for (ClientMethod dstMethod: client_methods.subList(i, client_methods.size())) {
//					Pair<String, Double> result = ASTEDClientFiles(sourceDir, outDir, apiPattern.getID(),
//							srcMethod.getFileName(), dstMethod.getFileName(), curPattern);
//				}
//			}
//			
//			for (ClientMethod srcMethod: client_methods) {
//				int i = client_methods.indexOf(srcMethod);
//				for (ClientMethod dstMethod: client_methods.subList(i, client_methods.size())) {
//					Pair<String, Double> result = null;
//					try {
//						result = ASTEDClientFiles(sourceDir, outDir, apiPattern.getID(),
//								srcMethod.getFileName(), dstMethod.getFileName(), srcMethod.getCallerName(), dstMethod.getCallerName(), curPattern);
//					} catch (IOException e) {
//						e.printStackTrace();
//					} catch (TreeTraversalException e) {
//						e.printStackTrace();
//					}
//					if (result != null) {
//						sOut.print(result.first);
//						eOut.print(String.format("%d	%d	%f%n", i, client_methods.indexOf(dstMethod), result.second));
//					}
//				}
//				 
//			}
			
			HashMap<String, Pair<ITree, TreeContext>> AST_map = new HashMap<String, Pair<ITree, TreeContext>>();
			for (ClientMethod method: client_methods) {
				Pair <ITree, TreeContext> subtree_pair = getSubTree(sourceDir,  outDir, method.getFileName(), method.getCallerName());
				AST_map.put(method.getFileName(), subtree_pair);
			}
			
			HashMap<String, ArrayList<Pair<String, String>>> subtree_embeddings_map = new HashMap<String, ArrayList<Pair<String, String>>>();
			for (ClientMethod method:client_methods) {
				ArrayList<Pair<String, String>> subtrees = getSubtreeEmbeddings(AST_map.get(method.getFileName()).first, AST_map.get(method.getFileName()).second, curPattern, max_api_calls);
				subtree_embeddings_map.put(method.getFileName(), subtrees);
			}
			System.out.println();
			
			
			client_methods.stream().forEach(srcMethod -> {
//				Pair <ITree, TreeContext> src_subtree_pair = getSubTree(sourceDir,  outDir, srcMethod.getFileName(), srcMethod.getCallerName());
//				ITree srcTree = src_subtree_pair.first;
//				TreeContext tc1 = src_subtree_pair.second;
//				ITree srcTree = AST_map.get(srcMethod.getFileName()).first;
//				TreeContext tc1 = AST_map.get(srcMethod.getFileName()).second;

				ArrayList<Pair<String, String>> subtrees1 = subtree_embeddings_map.get(srcMethod.getFileName());
				
				String srcFileName = srcMethod.getFileName();
				int i = client_methods.indexOf(srcMethod);
				Stream<ClientMethod> stream = StreamSupport.stream(client_methods.subList(i+1,  client_methods.size()).spliterator(), true);
				stream.forEach(dstMethod -> {
//					Double result = 0.0;
					Pair<Double, String> result = new Pair<Double, String>(0.0, "");
					
//					Pair <ITree, TreeContext> dst_subtree_pair = getSubTree(sourceDir,  outDir, dstMethod.getFileName(), dstMethod.getCallerName());
//					ITree dstTree = dst_subtree_pair.first;
//					TreeContext tc2 = dst_subtree_pair.second;
//					ITree dstTree = AST_map.get(dstMethod.getFileName()).first;
//					TreeContext tc2 = AST_map.get(dstMethod.getFileName()).second;
					String dstFileName = dstMethod.getFileName();
					
					ArrayList<Pair<String, String>> subtrees2 = subtree_embeddings_map.get(dstMethod.getFileName());
				
					try {
						result = fasterASTEDClientFiles(subtrees1, subtrees2);
//						result = fasterASTEDClientFiles(sourceDir, outDir, apiPattern.getID(), srcTree, dstTree, tc1, tc2, curPattern);
//						System.out.println(srcMethod.getFileName());
//						System.out.println(dstMethod.getFileName());
//						System.out.println(result);
//						System.out.println("");
					} catch (IOException e) {
						e.printStackTrace();
					} catch (TreeTraversalException e) {
						e.printStackTrace();
					}
					if (result.first != -1.0) {
						if (result.first == 1.0) {
							System.out.print(String.format("%s%n%s%n%s%n%n", srcMethod.getFileName(), dstMethod.getFileName(), "The distance was 0, check the files!"));
						}
						sOut.print(String.format("%s%n%s%n%f%n%s%n", srcMethod.getFileName(), dstMethod.getFileName(), result.first, result.second));
//						eOut.print(String.format("%d	%d	%d%n", i, client_methods.indexOf(dstMethod), result.second));
						eOut.print(String.format("%d	%d	%f%n", i, client_methods.indexOf(dstMethod), result.first));
					}
					else {
						System.out.print(String.format("%s%n%s%n%s%n%n", srcMethod.getFileName(), dstMethod.getFileName(), "Could not find matching sequence."));
					}
				});
			});
			
//			client_methods.stream().forEach(srcMethod -> {
//				int i = client_methods.indexOf(srcMethod);
//				Stream<ClientMethod> stream = StreamSupport.stream(client_methods.subList(i+1,  client_methods.size()).spliterator(), true);
//				stream.forEach(dstMethod -> {
////					Pair<String, Integer> result = null;
//					Pair<String, Double> result = null;
//					try {
//						result = ASTEDClientFiles(sourceDir, outDir, apiPattern.getID(),
//								srcMethod.getFileName(), dstMethod.getFileName(), srcMethod.getCallerName(), dstMethod.getCallerName(), curPattern);
////						result = compareClientFile(sourceDir, outDir, apiPattern.getID(),
////								srcMethod.getFileName(), dstMethod.getFileName(), curPattern);
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					} catch (TreeTraversalException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					if (result != null) {
//						if (result.second == -1.0) {
//							System.out.println("Could not find matching sequence.");
//						}
//						else {
//							sOut.print(result.first);
//	//						eOut.print(String.format("%d	%d	%d%n", i, client_methods.indexOf(dstMethod), result.second));
//							eOut.print(String.format("%d	%d	%f%n", i, client_methods.indexOf(dstMethod), result.second));
//						}
//					}
//					
//				});
//			});

			sOut.println("File Index Table");
			sOut.println();
			for (int i=0; i<client_methods.size(); i++) {
				sOut.print(String.format("%d \t %s %n", i, client_methods.get(i).getFileName()));
			}
			
			sOut.close();
			eOut.close();
		}
	}
	
	private static int getNumMethodCalls(ITree tree) {
		int num_calls = 0;
		for (ITree node: tree.getDescendants()) {
			if (node.getType() == 32 || node.getType() == 14) {
				num_calls += 1;
			}
		}
		return num_calls;
	}
	
	private static ArrayList<ITree> getSingleAPISubtree(
			final LinkedHashMap<String, ArrayList<ITree>> matchMap,
			final ArrayList<String> curPattern, final ITree tree, final TreeContext tc, final int max_api_calls) {
		ArrayList<ITree> subtrees = new ArrayList<ITree>();
		
		ArrayList<String> interestingTags = new ArrayList<String>();
		
		interestingTags.add("TryStatement");
		interestingTags.add("CatchClause");
		interestingTags.add("DoStatement");
		interestingTags.add("ForStatement");
		interestingTags.add("EnhancedForStatement");
		interestingTags.add("IfStatement");
		interestingTags.add("SwitchCase");
		interestingTags.add("SwitchStatement");
//		interestingTags.add("ThrowStatement");
		interestingTags.add("TryStatement");
		interestingTags.add("WhileStatement");
		interestingTags.add("MethodDeclaration");
		
		ArrayList<ITree> firstCalls = matchMap.get(getSimpleName(curPattern.get(0)));
		for (int i=0; i<firstCalls.size(); i++) {
			ITree cur_node = firstCalls.get(i);
//			int max_api_calls = 20;
			while ((cur_node.getId() != tree.getId()) && (!interestingTags.contains(tc.getTypeLabel(cur_node)))){
				if (getNumMethodCalls(cur_node.getParent()) > max_api_calls) {
					int num_method_calls = 0;
					List<ITree> siblings = cur_node.getParent().getChildren();
					List<ITree> new_siblings = new ArrayList<ITree>();
					new_siblings.add(cur_node);
					int cur_node_idx = cur_node.getParent().getChildPosition(cur_node);
					int margin = 1;
					while(num_method_calls < max_api_calls && (cur_node_idx - margin > 0 || cur_node_idx + margin < siblings.size())) {
						if (cur_node_idx - margin > 0) {
							num_method_calls = num_method_calls + getNumMethodCalls(siblings.get(cur_node_idx-margin));
							new_siblings.add(0, siblings.get(cur_node_idx-margin));
						}
						if (cur_node_idx + margin < siblings.size()) {
							num_method_calls = num_method_calls + getNumMethodCalls(siblings.get(cur_node_idx+margin));
							new_siblings.add(siblings.get(cur_node_idx+margin));
						}
						margin++;
					}
					cur_node = cur_node.getParent();
					cur_node.setChildren(new_siblings);
					break;
				}
				else {
					cur_node = cur_node.getParent();
				}
			}
			subtrees.add(cur_node);
		}
		
		return subtrees;
	}
	
	
	private static ArrayList<ITree> getAPTEDSubtree ( 
			final LinkedHashMap<String, ArrayList<ITree>> matchMap,
			final ArrayList<String> curPattern, final ITree tree1, final int max_api_calls) {
		
		for (Entry<String, ArrayList<ITree>> e: matchMap.entrySet()) {
			if (e.getValue().size() == 0) {
				return new ArrayList<ITree>();
			}
		}
		
//		for (int call_idx = 0; call_idx < curPattern.size(); call_idx++) {
//			ArrayList<ITree> curPairList = matchMap.get(getSimpleName(curPattern.get(call_idx)));
//			//sort based on the node idx
//		}
		
		ArrayList<ITree> firstCalls = matchMap.get(getSimpleName(curPattern.get(0)));
		ArrayList<Pair<Integer, Integer>> patternRanges = new ArrayList<Pair<Integer, Integer>>();
		for (int i=0; i<firstCalls.size(); i++) {
			int cur_max_idx = firstCalls.get(i).getId();
			ITree prev_it =  firstCalls.get(i);
			boolean is_ascending = true;
			for (int j=1; j<curPattern.size(); j++) {
				if (!is_ascending) {
					break;
				}
				ArrayList<ITree> curPairList = matchMap.get(getSimpleName(curPattern.get(j)));
				for (ITree it : curPairList) {
					if (cur_max_idx < it.getId()) {
						cur_max_idx = it.getId();
						is_ascending = true;
						prev_it = it;
						break;
					}
					else {
						if (!it.getParent().getDescendants().contains(prev_it))
							is_ascending = false;
					}
				}
				
			}
			if (is_ascending) {
				patternRanges.add(new Pair<Integer, Integer>(firstCalls.get(i).getId(), cur_max_idx));
			}
		}
		
		ArrayList<Pair<Integer, Integer>> removeIdx = new ArrayList<Pair<Integer, Integer>>();
		for (int i=0; i<patternRanges.size()-1; i++) {
			if (patternRanges.get(i).first <= patternRanges.get(i+1).first 
					&& patternRanges.get(i).second.equals(patternRanges.get(i+1).second) ) {
				removeIdx.add(patternRanges.get(i));
			}
		}
		for (Pair<Integer, Integer> r: removeIdx) {
			patternRanges.remove(r);
		}
		
		ArrayList<ITree> subtrees = new ArrayList<ITree>();
		for (Pair<Integer, Integer> indices : patternRanges) {
			subtrees.add(getEncompassingSubtreeRange(tree1, indices.first, indices.second));
//			subtrees.add(getEncompassingSubtreeRange(tree1, indices.first, indices.second, max_api_calls));
		}
		
		
		return subtrees;
	}
	
	private static Pair<ITree, TreeContext> getSubTree(final String sourceDir, final String outDir, final String srcFileName, final String srcCallerName) {
		Pair<ITree, TreeContext> result;
		try {
			result = _getSubTree(sourceDir, outDir, srcFileName, srcCallerName);
		} catch (IOException e) {
			result = null;
			e.printStackTrace();
		}
		return result;
	}
	
	private static Pair<ITree, TreeContext> _getSubTree(final String sourceDir, final String outDir, final String srcFileName, final String srcCallerName) throws IOException {
		String srcFile = sourceDir + srcFileName + ".java";
		
		File srcAST = new File(outDir + "AST/" + srcFileName + ".ast");
		TreeContext tc1;
		ITree tree1;
		Boolean isSrcASTExist = srcAST.exists();
		
		if (isSrcASTExist) {
			try {
				TreeGenerator g = TreeIoUtils.fromXml();
				tc1 = g.generateFromFile(srcAST);
				tree1 = tc1.getRoot();
			} catch(NullPointerException e) {
				tc1 = Generators.getInstance().getTree(srcFile); 
				tree1 = tc1.getRoot();
				System.out.println("Could not read AST file: " + srcFileName + ", " + srcCallerName);
			}
		}
		else {
			tc1 = Generators.getInstance().getTree(srcFile); 
			tree1 = tc1.getRoot();
			
			TreeSerializer g = TreeIoUtils.toXml(tc1);
			try {
				g.writeTo(srcAST);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Boolean foundMethod = false;
		for (final ITree it: tree1.getChild(tree1.getChildren().size()-1).getChildren()) {
			for (final ITree cit: it.getChildren()) {
				if (tc1.getTypeLabel(cit).equals("SimpleName") && cit.getLabel().equals(srcCallerName)) {
					foundMethod = true;
//					tree1 = it.getChild(it.getChildren().size()-1);
					tree1 = it;
					break;
				}
			}
		}
		if (!foundMethod) {
			for (int c_idx = tree1.getChildren().size()-2; c_idx>=0; c_idx--) {
				if(!foundMethod) {
					for (final ITree it: tree1.getChild(c_idx).getChildren()) {
						if(!foundMethod) {
							for (final ITree cit: it.getChildren()) {
								if (tc1.getTypeLabel(cit).equals("SimpleName") && cit.getLabel().equals(srcCallerName)) {
									foundMethod = true;
									tree1 = tree1.getChild(c_idx);
									break;
								}
							}
						}
					}
				}
			}
		}
		if (!foundMethod) {
//			System.out.println("Cannot find caller method from AST, use the whole AST");
//			System.out.println(srcFileName + ", " + srcCallerName);
//			throw new TreeTraversalException("Cannot find caller method from AST: " + srcFileName + ", " + srcCallerName);
		}
		return new Pair<ITree, TreeContext> (tree1, tc1);
	}
	
	private static ArrayList<Pair<String, String>> getSubtreeEmbeddings(final ITree tree1, final TreeContext tc1, final ArrayList<String> curPattern, final int max_api_calls) {
		
		ArrayList<Pair<String, String>> subtreeEmbeddings = new ArrayList<Pair<String, String>>();
		LinkedHashMap<String, ArrayList<ITree>> matchMap1 = new LinkedHashMap<String, ArrayList<ITree>>();
		for (final String call: curPattern) {
			matchMap1.put(getSimpleName(call), new ArrayList<ITree>());
		}
		for (final ITree it: tree1.preOrder()) {
			if (tc1.getTypeLabel(it.getType()).equals("SimpleName")) {					
				ArrayList<ITree> curList = matchMap1.get(it.getLabel());
				if (curList != null) {
					curList.add(it);
				}
			}
		}
		ArrayList<ITree> subtrees1 = null; 
		if (curPattern.size() == 1) {		
			subtrees1 = getSingleAPISubtree(matchMap1, curPattern, tree1, tc1, max_api_calls);
		}
		else {
			subtrees1 = getAPTEDSubtree(matchMap1, curPattern, tree1, max_api_calls);
		}
		if (subtrees1.size() == 0) {
			return subtreeEmbeddings;
		}

		for (ITree it1 : subtrees1) {
			String trans1 = formTransaction(tc1, it1, "", 0, true);
			int num_nodes = StringUtils.countMatches(trans1, "{"); 
			if (num_nodes <= 3) {
				continue;
			}
			String positions = String.format("%d, %d, %d", it1.getPos(), it1.getEndPos(), num_nodes); 
			subtreeEmbeddings.add(new Pair<String, String>(trans1, positions));
		}
		return subtreeEmbeddings;
		
	}	

	private static Pair<Double, String> fasterASTEDClientFiles(final ArrayList<Pair<String, String>> subtrees1, 
			final ArrayList<Pair<String,String>> subtrees2) throws IOException, TreeTraversalException {	
			// Parse the input and transform to Node objects storing node information in MyNodeData.
			InputParser<StringNodeData> parser = new BracketStringInputParser();
			APTED<PerEditOperationStringNodeDataCostModel, StringNodeData> apted = new APTED<PerEditOperationStringNodeDataCostModel, StringNodeData>(new PerEditOperationStringNodeDataCostModel(1, 1, 1));
			
			if (subtrees1.size() == 0 || subtrees2.size() == 0) {
				return new Pair<Double, String>(-1.0, "");
			}
			
			
			double minDistance = Double.MAX_VALUE;
			String srcPositions = "";
			String dstPositions = "";
			
			for (Pair<String, String> it1 : subtrees1) {
				for (Pair<String, String> it2 : subtrees2) {
					String trans1 = it1.first;
					String trans2 = it2.first;
//					System.out.println(trans1);
//					System.out.println(trans2);
					
					Node<StringNodeData> t1 = parser.fromString(trans1);
					Node<StringNodeData> t2 = parser.fromString(trans2);
					// Initialise APTED.
//					APTED<PerEditOperationStringNodeDataCostModel, StringNodeData> apted = new APTED<PerEditOperationStringNodeDataCostModel, StringNodeData>(new PerEditOperationStringNodeDataCostModel(1, 1, 1));
					// Execute APTED.
					double result = apted.computeEditDistance(t1, t2);
					
					apted.init(t1, t2);
					
					if (minDistance > result) {
						minDistance = result;
						srcPositions = it1.second;
						dstPositions = it2.second;
					}
				}
			}
			String positions = String.format("%s, %s%n", srcPositions, dstPositions);
			
			double similarity = 0;
			if (minDistance != 0) {
				similarity = (double)1 / java.lang.Math.exp((double)minDistance*0.1);
			}
			else {
				return new Pair<Double, String>(1.0, positions);
				
			}
			
			return new Pair<Double, String>(similarity, positions);
	
	}
	
	
	private static String formTransaction(TreeContext tc, ITree t, String p_tag, Integer child_num, Boolean is_root) {
		String result = "";
		String tag = tc.getTypeLabel(t.getType());
		
		if (tag.equals("SimpleName")) {
//			if ((p_tag.equals("MethodInvocation") && child_num < 2) ||
			if ((p_tag.equals("MethodInvocation") && child_num == 1) ||
				(p_tag.equals("ClassInstanceCreation") && child_num == 0) ){
			result = result + "{" + t.getLabel() + "}";
			return result;
//			result = result + " " + t.getLabel();
			}
		}
		ArrayList<String> interestingTags = new ArrayList<String>();
		
		interestingTags.add("TryStatement");
		interestingTags.add("CastExpression");
		interestingTags.add("CatchClause");
		interestingTags.add("ConditionalExpression");
		interestingTags.add("DoStatement");
		interestingTags.add("ForStatement");
		interestingTags.add("EnhancedForStatement");
		interestingTags.add("IfStatement");
		interestingTags.add("SwitchCase");
		interestingTags.add("SwitchStatement");
		interestingTags.add("ThrowStatement");
		interestingTags.add("WhileStatement");
//		interestingTags.add("MethodInvocation");
//		interestingTags.add("ClassInstanceCreation");
//		interestingTags.add("InfixExpression");
//		interestingTags.add("NullLiteral");
//		interestingTags.add("StringLiteral");
		
		ArrayList<String> interestingParentTags = new ArrayList<String>();
		interestingParentTags.add("CatchClause");
		interestingParentTags.add("IfStatement");
		interestingParentTags.add("SwitchCase");
		
		
		ArrayList<String> loopTags = new ArrayList<String>();
		loopTags.add("ForStatement");
		loopTags.add("WhileStatement");
		
		
		if (interestingTags.contains(tag)) {
			if (tag.equals("DoStatement") || tag.equals("EnhancedForStatement") || loopTags.contains(tag))
				result = result + "{Loop";
			else
				result = result + "{" + tag;
		}
		// TODO: try-with, try-catch-finally ... 
//		else if (!interestingTags.contains(tag) && p_tag.equals("TryStatement")) {
//			if (t.getParent().getChildren().size() > 0){
//				if (child_num == 0)
//					result = result + "{with";
//				else if (child_num == t.getParent().getChildren().size()-1) 
//					result = result + "}{Execute";
//			}
//			else {
//				
//			}
//			
//		}
		else if (!interestingTags.contains(tag) && interestingParentTags.contains(p_tag)) {
			if (child_num == 0) {
				result = result + "{Condition";
			}
			else {
				result = result + "{Execute";
			}
			
		}
		else if (!interestingTags.contains(tag) && loopTags.contains(p_tag)) {
			if (child_num == 0) {
				result = result + "{Condition";
			}
			else if (child_num == t.getParent().getChildren().size()-1 && child_num != 0) {
				result = result + "{Execute";
			}
		}
		else if (!interestingTags.contains(tag) && p_tag.equals("EnhancedForStatement")) {
			if (child_num == 0) {
				result = result + "{Condition";
			}
			else if (child_num == 2) {
				result = result + "{Execute";
			}
		}
		else if (!interestingTags.contains(tag) && p_tag.equals("DoStatement")) {
			if (child_num == 0) {
				result = result + "{Execute";
			}
			else if (child_num == 1) {
				result = result + "{Condition";
			}
		}
		


		int child_count = 0;
		//TODO: cannot extract the API call only, but need to add params / calling objs
		if (tag.equals("MethodInvocation")) {
			if (t.getChildren().size() > 2) {
				for (int child_idx=2; child_idx < t.getChildren().size(); child_idx++) {
					result = result + formTransaction(tc, t.getChild(child_idx), tag, child_idx, false);
				}
				result = result + formTransaction(tc, t.getChild(0), tag, 0, false);
				result = result + formTransaction(tc, t.getChild(1), tag, 1, false);
			}
			else if (t.getChildren().size() == 2 ) {
				result = result + formTransaction(tc, t.getChild(0), tag, 0, false);
				result = result + formTransaction(tc, t.getChild(1), tag, 1, false);
			}
			else if (t.getChildren().size() == 1) {
				// If we use both [0] and [1] in comparison, the similarity score will fluctuate when client code use different variable names 
				// ignore [0] -> sacrifice some API calls in this case: [api_call](params)
				// but since all client code will contain this api_call, wouldn't make a lot of difference
//				result = result + formTransaction(tc, t.getChild(0), tag, 0, false); 
				result = result + formTransaction(tc, t.getChild(0), tag, 1, false);
			}
			else {
				System.out.println("ERROR: Method Invocation has wrong format!");
			}
			
		}
		else if (tag.equals("ClassInstanceCreation")) {
			if (t.getChildren().size() > 1) {
				for (int child_idx=1; child_idx < t.getChildren().size(); child_idx++) {
					result = result + formTransaction(tc, t.getChild(child_idx), tag, child_idx, false);
				}
			}
			result = result + "{Class" + formTransaction(tc, t.getChild(0), tag, 0, false) + "}";
		}
		else {
			for (ITree ch: t.getChildren()) {
				if (tag.equals("SimpleType") && p_tag.equals("ClassInstanceCreation")) {
					result = result + formTransaction(tc, ch, p_tag, child_count, false);
				}
				else {
					result = result + formTransaction(tc, ch, tag, child_count, false);
				}
				child_count = child_count + 1;
			}
		}
		
		//TODO: separate the case baed on the child num
		if (interestingTags.contains(tag)) {
			result = result + "}";
		}
		else if (interestingParentTags.contains(p_tag)){
			result = result + "}";
		}
		else if (loopTags.contains(p_tag) && ((child_num == 0) || (child_num == t.getParent().getChildren().size()-1) )){
			result = result + "}";
		}
		else if (p_tag.equals("EnhancedForStatement") && ((child_num == 1) || (child_num == t.getParent().getChildren().size()-1) )){
			result = result + "}";
		}
		else if (p_tag.equals("DoStatement") && ((child_num == 0) || (child_num == t.getParent().getChildren().size()-1) )){
			result = result + "}";
		}
		if (is_root) {
			result = "{Root" + result + "}";
		}
		return result;
	}
	
	private static ITree getEncompassingSubtreeRange(ITree t, int start_idx, int end_idx) {
		ITree st = t;
		boolean isContinue = true;
		
		while (isContinue) {
			List<ITree> curChildren = st.getChildren();
			isContinue = false;
			for (int i=1; i<curChildren.size(); i++) {
				if (curChildren.get(i-1).getId() < start_idx && curChildren.get(i).getId() > end_idx) {
					st = curChildren.get(i);
					isContinue = true;
					break;
				}
			}
		}
		
		return st;
				
	}
	
//	private static ITree getEncompassingSubtreeRange(ITree t, int start_idx, int end_idx, int max_api_calls) {
//		ITree st = t;
//		boolean isContinue = true;
//		
//		while (isContinue) {
//			List<ITree> curChildren = st.getChildren();
//			isContinue = false;
//			if (curChildren.size() == 1) {
//				st = curChildren.get(0);
//				isContinue = true;
//			}
//			else if (curChildren.size() == 2) {
//				if (curChildren.get(1).getId() > end_idx) {
//					st = curChildren.get(0);
//					isContinue = true;
//				}
//				else if (curChildren.get(0).getId() < start_idx) {
//					st = curChildren.get(1);
//					isContinue = true;
//				}
//			}
//			else {
//				for (int i=1; i<curChildren.size(); i++) {
//					if (curChildren.get(i-1).getId() < start_idx && curChildren.get(i).getId() > end_idx) {
//						st = curChildren.get(i);
//						isContinue = true;
//						break;
//					}
//				}
//			}
//		}
//
//		if (getNumMethodCalls(st) > max_api_calls) {
//			int num_method_calls = 0;
//			List<ITree> siblings = st.getChildren();
//			List<ITree> new_siblings = new ArrayList<ITree>();
//			for (int i=0; i<st.getChildren().size(); i++)  {
//				if (siblings.get(i).getId() > start_idx && siblings.get(i).getId() < end_idx ) {
//					new_siblings.add(st.getChild(i));
//				}
//				else if (i-1 >= 0) {
//					if (siblings.get(i-1).getId() < end_idx) {
//						new_siblings.add(st.getChild(i));
//					}
//					
//				}
//			}
//			if (new_siblings.size() > 0) {
//				int sibling_start_idx = st.getChildPosition(new_siblings.get(0));
//				int sibling_end_idx = st.getChildPosition(new_siblings.get(new_siblings.size()-1));
//				int margin = 1;
//				while(num_method_calls < max_api_calls && (sibling_start_idx - margin > 0 || sibling_end_idx + margin < siblings.size())) {
//					if (sibling_start_idx - margin > 0) {
//						num_method_calls = num_method_calls + getNumMethodCalls(siblings.get(sibling_start_idx-margin));
//						new_siblings.add(0, siblings.get(sibling_start_idx-margin));
//					}
//					if (sibling_end_idx + margin < siblings.size()) {
//						num_method_calls = num_method_calls + getNumMethodCalls(siblings.get(sibling_end_idx+margin));
//						new_siblings.add(siblings.get(sibling_end_idx+margin));
//					}
//					margin++;
//				}
//				st.setChildren(new_siblings);
//			}
//		}
//		
//		return st;
//				
//	}
	
	private static String getSimpleName(String fqname) {
		String[] fullName = fqname.split("\\.");
		if (fullName[fullName.length - 1].equals("<init>")) {
			return fullName[fullName.length - 2];
		}
		else {
			return fullName[fullName.length - 1];
		}
	}
	
	private static ITree getPrevSibling(ITree curNode) {
		if (curNode.positionInParent() > 0) {
			return curNode.getParent().getChild(curNode.positionInParent()-1);
		}
		else if (curNode.positionInParent() == -1) {
			return curNode;
		}
		else if (curNode.positionInParent() == 0) { 
			return getPrevSibling(curNode.getParent()); 
		}
		else {
			return null;
		}
	}

	
	private static void readLogFile(final String logFile, HashMap<Integer, APIPattern> dictionary) throws IOException {

		boolean found = false;
		final LineIterator it = FileUtils.lineIterator(new File(logFile));
		
		/** dayen: save meta_data of each transaction */
		int pattern_idx = -1;
//		boolean isOdd = true;
		boolean readPattern = false;
		
		while (it.hasNext()) {
			final String line = it.nextLine();

			if (found) {
				if (line.contains("pattern ") && !line.contains("biased ")) {
					pattern_idx = Integer.parseInt(line.split("pattern ")[1].replaceAll("\\s","")); 
					readPattern = true;
				}
				/** dayen: add pattern index and patterns into dictionary */
				else if (line.contains("[") && line.contains("]") && readPattern) {
					dictionary.put(pattern_idx, new APIPattern(pattern_idx, line));
					readPattern = false;
				}
				
				else if (line.contains("file:")) {
					dictionary.get(pattern_idx).addClientMethod(new ClientMethod(line));
				}
				else if (line.contains("prob: ")) {
					dictionary.get(pattern_idx).setProbInt(line);
				}
				else if (line.contains("support: ")) {
					dictionary.get(pattern_idx).setSupNumprj(line);
				}
				else if (line.equals("\n")) {
					continue;
				}
				else if (line.contains("biased")) {
					continue;
				}
				else if (line.contains("Filtered SEQUENCES")) {
					break;
				}
				else {
//					System.out.println(line);
				}
		
			}

			if (line.contains("INTERESTING SEQUENCES"))
				found = true;

		}
		it.close();
	}
}
