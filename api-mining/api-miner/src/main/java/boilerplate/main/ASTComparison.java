package boilerplate.main;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import apimining.pam.main.PAM.LogLevelConverter;
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
import com.github.gumtreediff.io.ActionsIoUtils;
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
	}
	
	public static void main(final String[] args) throws Exception{
		// Runtime parameters
		final Parameters params = new Parameters();
		final JCommander jc = new JCommander(params);

		/** dayen: limit the number of threads */
//		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", Integer.toString(params.numThread));
		
		try {
			jc.parse(args);

			System.out.println("Processing " + FilenameUtils.getBaseName(params.logFile) + "...");
			compareASTs(params.logFile, params.sourceDir, params.outDir, params.patternStart, params.patternLimit, params.numThread, params.biasThreshold);

		} catch (final ParameterException e) {
			System.out.println(e.getMessage());
			jc.usage();
		}
	}
	
	private static void compareASTs(final String logFile, final String sourceDir, final String outDir, 
			final int patternStart, final int patternLimit, final int numThread, final int biasThreshold) throws Exception {
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
			
			final APIPattern apiPattern = entry.getValue();
			final ArrayList<ClientMethod> client_methods = new ArrayList<ClientMethod>();
			sOut.println(apiPattern.getPatternInString());
			sOut.println();
			
			System.out.println();
			apiPattern.printPattern();
			final ArrayList<String> curPattern = apiPattern.getPattern();
			
			if (curPattern.size() == 1) {
				continue;
			}

			
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
			
			for (ClientMethod srcMethod : client_methods) {
				int i = client_methods.indexOf(srcMethod);
				for (ClientMethod dstMethod: client_methods.subList(i, client_methods.size())) {
					Pair<String, Double> result = ASTEDClientFiles(sourceDir, outDir, apiPattern.getID(),
							srcMethod.getFileName(), dstMethod.getFileName(), curPattern);
				}
			}
			
			
//			client_methods.stream().forEach(srcMethod -> {
//				int i = client_methods.indexOf(srcMethod);
//				Stream<ClientMethod> stream = StreamSupport.stream(client_methods.subList(i+1,  client_methods.size()).spliterator(), true);
//				stream.forEach(dstMethod -> {
////					Pair<String, Integer> result = null;
//					Pair<String, Double> result = null;
//					try {
//						result = ASTEDClientFiles(sourceDir, outDir, apiPattern.getID(),
//								srcMethod.getFileName(), dstMethod.getFileName(), curPattern);
////						result = compareClientFile(sourceDir, outDir, apiPattern.getID(),
////								srcMethod.getFileName(), dstMethod.getFileName(), curPattern);
//					} catch (IOException e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//					if (result != null) {
//						sOut.print(result.first);
////						eOut.print(String.format("%d	%d	%d%n", i, client_methods.indexOf(dstMethod), result.second));
//						eOut.print(String.format("%d	%d	%f%n", i, client_methods.indexOf(dstMethod), result.second));
//					}
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
	
	private static Pair<ITree, ITree> getSingletonSubtrees(final MappingStore ms, 
			final LinkedHashMap<String, ArrayList<Pair<ITree, ITree>>> matchMap,
			final ArrayList<String> curPattern) {
		
		ITree subtree1;
		ITree subtree2;
		
		ArrayList<Pair<ITree, ITree>> curPairList = matchMap.get(getSimpleName(curPattern.get(0)));
		if (curPairList.size() == 0) {
			System.out.println("This pattern is not matched in the client code");
			return null;
		}
		
		int maxRange = 0;
		subtree1 = curPairList.get(0).first;
		subtree2 = curPairList.get(0).second;
		
		for(Pair<ITree, ITree> p : curPairList) {
			ITree mappedParent1 = p.first;
			ITree mappedParent2 = p.second;
			while (ms.hasDst(mappedParent2.getParent()) && mappedParent1.getParent().equals(ms.getSrc(mappedParent2.getParent()))) {
				mappedParent1 = mappedParent1.getParent();
				mappedParent2 = mappedParent2.getParent();
			}
			if (maxRange < (mappedParent1.getId() - getPrevSibling(mappedParent1).getId() + 
					mappedParent2.getId() - getPrevSibling(mappedParent2).getId())) {
						subtree1 = mappedParent1;
						subtree2 = mappedParent2;
			}
			
		}
		return new Pair<ITree, ITree>(subtree1, subtree2); 
	}
	
	private static Pair<ITree, ITree> getMulticallSubtrees (final MappingStore ms, 
			final LinkedHashMap<String, ArrayList<Pair<ITree, ITree>>> matchMap,
			final ArrayList<String> curPattern, final ITree tree1, ITree tree2) {
		
		ITree subtree1;
		ITree subtree2;
		
		Boolean foundSingle = false;
		for (int call_idx = 1; call_idx < curPattern.size(); call_idx++) {
			ArrayList<Pair<ITree, ITree>> curPairList = matchMap.get(getSimpleName(curPattern.get(call_idx)));
			int referIdx1 = -1;
			int referIdx2 = -1;
			if (curPairList.size() == 1) {
				foundSingle = true;
				Pair<ITree, ITree> referPair = matchMap.get(getSimpleName(curPattern.get(call_idx))).get(0);
				referIdx1 = referPair.first.getId();
				referIdx2 = referPair.second.getId();
				
				continue;
			}
			else if (curPairList.size() == 0) {
				System.out.println(curPattern.get(call_idx));
				System.out.println("Error: This API call is not matched in this client code.");
				continue;
			}
			else if (curPairList.size() > 1) {
				if (foundSingle) {
					int diff1 = Integer.MAX_VALUE;
					int diff2 = Integer.MAX_VALUE;
					int chosenIndex = -1;
					
					for (int pair_idx = 0; pair_idx < curPairList.size(); pair_idx++) {
						int new_diff1 = Math.abs(curPairList.get(pair_idx).first.getId() - referIdx1);
						int new_diff2 = Math.abs(curPairList.get(pair_idx).second.getId() - referIdx2);
						if (new_diff1 < diff1 && new_diff2 < diff2) {
							diff1 = new_diff1;
							diff2 = new_diff2;
							chosenIndex = pair_idx;
						}
					}
					
//					System.out.println(chosenIndex);
					if (chosenIndex == -1) {
						System.out.println(curPattern.get(call_idx));
						System.out.println("Error: Matched noe is not in the same sub-tree");
					}
					else {
						Pair<ITree, ITree> tempPair = curPairList.get(0);
						matchMap.get(getSimpleName(curPattern.get(call_idx))).set(0, curPairList.get(chosenIndex));
						matchMap.get(getSimpleName(curPattern.get(call_idx))).set(chosenIndex, tempPair);
					}
				}
				else {
					if (call_idx == curPattern.size()-1) {
						int max1 = 0;
						int max2 = 0;
						int chosenIndex = -1;
						for (int pair_idx = 0; pair_idx < curPairList.size(); pair_idx++) {
							if (curPairList.get(pair_idx).first.getId() > max1) { 
//											&& curPairList.get(pair_idx).second.getId() > max2) {
								max1 = curPairList.get(pair_idx).first.getId();
								max2 = curPairList.get(pair_idx).second.getId();
								chosenIndex = pair_idx;
							}
						}
						Pair<ITree, ITree> tempPair = curPairList.get(0);
						matchMap.get(getSimpleName(curPattern.get(call_idx))).set(0, curPairList.get(chosenIndex));
						matchMap.get(getSimpleName(curPattern.get(call_idx))).set(chosenIndex, tempPair);
					}
					continue;
				}
			}
		}
		if (matchMap.get(getSimpleName(curPattern.get(0))).size() == 0 || 
				matchMap.get(getSimpleName(curPattern.get(curPattern.size()-1))).size() == 0) {
			System.out.println("ERROR: This client code does not contain the whole pattern.");
			return null;
		}
		if (!foundSingle) {
			System.out.println("Error: There are more than one pattern in this method. User the last pattern");
		}
		
		Pair<ITree, ITree> referPair = matchMap.get(getSimpleName(curPattern.get(curPattern.size()-1))).get(0);
		int referIdx1 = referPair.first.getId();
		int referIdx2 = referPair.second.getId();
		for (int call_idx = curPattern.size()-2; call_idx >= 0; call_idx--) {
			ArrayList<Pair<ITree, ITree>> curPairList = matchMap.get(getSimpleName(curPattern.get(call_idx)));
			
			if (curPairList.size() == 1) {
				foundSingle = true;
				referPair = matchMap.get(getSimpleName(curPattern.get(call_idx))).get(0);
				referIdx1 = referPair.first.getId();
				referIdx2 = referPair.second.getId();
				
				continue;
			}
			else if (curPairList.size() == 0) {
				System.out.println("Error2: Could not find pattern");
				continue;
			}
			else if (curPairList.size() > 1) {
				int diff1 = Integer.MAX_VALUE;
				int diff2 = Integer.MAX_VALUE;
				int chosenIndex = -1;
				
				for (int pair_idx = 0; pair_idx < curPairList.size(); pair_idx++) {
					int new_diff1 = Math.abs(curPairList.get(pair_idx).first.getId() - referIdx1);
					int new_diff2 = Math.abs(curPairList.get(pair_idx).second.getId() - referIdx2);
					if (new_diff1 < diff1 && new_diff2 < diff2) {
						diff1 = new_diff1;
						diff2 = new_diff2;
						chosenIndex = pair_idx;
					}
				}
				
//				System.out.println(chosenIndex);
				if (chosenIndex == -1) {
					System.out.println(curPattern.get(call_idx));
					System.out.println("Error: Matched noe is not in the same sub-tree");
				}
				else {
					Pair <ITree, ITree> tempPair = curPairList.get(0);
					matchMap.get(getSimpleName(curPattern.get(call_idx))).set(0, curPairList.get(chosenIndex));
					matchMap.get(getSimpleName(curPattern.get(call_idx))).set(chosenIndex, tempPair);
				}
				
			}
		}
		if (matchMap.get(getSimpleName(curPattern.get(0))).size() == 0 || 
				matchMap.get(getSimpleName(curPattern.get(curPattern.size()-1))).size() == 0) {
			System.out.println("ERROR: This client code does not contain the whole pattern.");
			return null;
		}
		
		int firstStartIdx = matchMap.get(getSimpleName(curPattern.get(0))).get(0).first.getId();
		int firstEndIdx = matchMap.get(getSimpleName(curPattern.get(curPattern.size()-1))).get(0).first.getId();
		int secondStartIdx = matchMap.get(getSimpleName(curPattern.get(0))).get(0).second.getId();
		int secondEndIdx = matchMap.get(getSimpleName(curPattern.get(curPattern.size()-1))).get(0).second.getId();
		
		subtree1 = getEncompassingSubtreeRange(tree1, firstStartIdx, firstEndIdx);
		subtree2 = getEncompassingSubtreeRange(tree2, secondStartIdx, secondEndIdx);
		
		return new Pair<ITree, ITree>(subtree1, subtree2);
	}
	
	private static Pair<String, Integer> compareClientFile(final String sourceDir, final String outDir, final int patternIdx, 
			final String srcFileName, final String dstFileName, final ArrayList<String> curPattern) throws IOException {
		String srcFile = sourceDir + srcFileName + ".java";
		TreeContext tc1 = Generators.getInstance().getTree(srcFile); 
		ITree tree1 = tc1.getRoot();
		String dstFile = sourceDir + dstFileName + ".java";
		TreeContext tc2 = Generators.getInstance().getTree(dstFile); 
		ITree tree2 = tc2.getRoot();
			
		String diff_file = outDir + Integer.toString(patternIdx) + "_" + srcFileName + "_" + dstFileName + ".txt";
		
		Pair<ITree, ITree> subtrees;
		if (curPattern.size() == 1) {
//			subtrees = getSingletonSubtrees(ms, matchMap, curPattern);
			String sResult = String.format("%s%n%s%n%d%n%f%n%f%n%f%n%n", srcFileName, dstFileName, 0, 0.0, 0.0, 0.0);
			return new Pair<String, Integer>(sResult, 0);
		}
//		else if (curPattern.size() > 1) {
		else {
			
			Matcher m = Matchers.getInstance().getMatcher(tree1, tree2);
			m.match();
		
			MappingStore ms = m.getMappings();
					
			LinkedHashMap<String, ArrayList<Pair<ITree, ITree>>> matchMap = new LinkedHashMap<String, ArrayList<Pair<ITree, ITree>>>(); 
					
			for (final String call: curPattern) {
				matchMap.put(getSimpleName(call), new ArrayList<Pair<ITree, ITree>>());
			}
					
			for (final Mapping mp: ms.asSet() ) {
				if (tc1.getTypeLabel(mp.first.getType()).equals("SimpleName") && 
					tc2.getTypeLabel(mp.second.getType()).equals("SimpleName")) {
					
					if (mp.first.getLabel().equals(mp.second.getLabel())) {
						ArrayList<Pair<ITree, ITree>> curList = matchMap.get(mp.first.getLabel());
						if (curList != null) {
							curList.add(new Pair<ITree, ITree>(mp.first, mp.second));
						}
					}
				}
			}
			
			subtrees = getMulticallSubtrees (ms, matchMap, curPattern, tree1, tree2);
			
			if (subtrees == null) {
				String sResult = String.format("%s%n%s%n%d%n%f%n%f%n%f%n%n", srcFileName, dstFileName, 0, 0.0, 0.0, 0.0);
				return new Pair<String, Integer>(sResult, 0);
			}
			
			ActionGenerator g = new ActionGenerator(tree1, tree2, ms);
			g.generate();
			List<Action> actions = g.getActions();
			
			try {
				ActionsIoUtils.ActionSerializer serializer = ActionsIoUtils.toText(
	                    tc1, actions, ms);
	            serializer.writeTo(diff_file);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
			int numDesc = numberOfCommonDescendants(subtrees.first, subtrees.second, ms);
			double chawathe = chawatheSimilarity(subtrees.first, subtrees.second, numDesc);
			double dice = diceSimilarity(subtrees.first, subtrees.second, numDesc);
			double jaccard = jaccardSimilarity(subtrees.first, subtrees.second, numDesc);
			
			String sResult = String.format("%s%n%s%n%d%n%f%n%f%n%f%n%n", srcFileName, dstFileName, numDesc, chawathe, dice, jaccard);
			System.out.println(diff_file);
			System.out.println(numDesc);
			System.out.println(chawathe);
			System.out.println(dice);
			System.out.println(jaccard);
			System.out.println();
			
			if (numDesc < 5) {
				numDesc = 0;
			}
			
			return new Pair<String, Integer>(sResult, numDesc);
		}
	}
	
	private static ArrayList<ITree> getAPTEDSubtree ( 
			final LinkedHashMap<String, ArrayList<ITree>> matchMap,
			final ArrayList<String> curPattern, final ITree tree1) {
		
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
						break;
					}
					else {
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
			int a = patternRanges.get(i).first;
			int b = patternRanges.get(i+1).first;
			int c = patternRanges.get(i).second;
			int d = patternRanges.get(i+1).second;
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
		}
		
		
		return subtrees;
	}
	
	private static Pair<String, Double> ASTEDClientFiles(final String sourceDir, final String outDir, final int patternIdx, 
			final String srcFileName, final String dstFileName, final ArrayList<String> curPattern) throws IOException {
			String srcFile = sourceDir + srcFileName + ".java";
			TreeContext tc1 = Generators.getInstance().getTree(srcFile); 
			ITree tree1 = tc1.getRoot();
			String dstFile = sourceDir + dstFileName + ".java";
			TreeContext tc2 = Generators.getInstance().getTree(dstFile); 
			ITree tree2 = tc2.getRoot();
				
//			String diff_file = outDir + Integer.toString(patternIdx) + "_" + srcFileName + "_" + dstFileName + ".txt";
			
			Launcher launcher = new Launcher();
			launcher.addInputResource(srcFile);
			launcher.buildModel();
			CtModel model = launcher.getModel();
			
//			ControlFlowBuilder builder = new ControlFlowBuilder();
//			ControlFlowGraph graph = builder.build();
			
			if (curPattern.size() == 1) {
	//			subtrees = getSingletonSubtrees(ms, matchMap, curPattern);
				String sResult = String.format("%s%n%s%n%f%n%n", srcFileName, dstFileName, 0.0);
				return new Pair<String, Double>(sResult, 0.0);
			}
			else {
						
				LinkedHashMap<String, ArrayList<ITree>> matchMap1 = new LinkedHashMap<String, ArrayList<ITree>>();
				LinkedHashMap<String, ArrayList<ITree>> matchMap2 = new LinkedHashMap<String, ArrayList<ITree>>();
						
				for (final String call: curPattern) {
					matchMap1.put(getSimpleName(call), new ArrayList<ITree>());
					matchMap2.put(getSimpleName(call), new ArrayList<ITree>());
				}
				
				if (matchMap1.keySet().size() == 1) {
					String sResult = String.format("%s%n%s%n%f%n%n", srcFileName, dstFileName, 0.0);
					return new Pair<String, Double>(sResult, 0.0);
				}
						
				for (final ITree it: tree1.preOrder()) {
					if (tc1.getTypeLabel(it.getType()).equals("SimpleName")) {					
						ArrayList<ITree> curList = matchMap1.get(it.getLabel());
						if (curList != null) {
							curList.add(it);
						}
					}
				}
				for (final ITree it: tree2.preOrder()) {
					if (tc2.getTypeLabel(it.getType()).equals("SimpleName")) {					
						ArrayList<ITree> curList = matchMap2.get(it.getLabel());
						if (curList != null) {
							curList.add(it);
						}
					}
				}
		
				ArrayList<ITree> subtrees1 = getAPTEDSubtree(matchMap1, curPattern, tree1);
				ArrayList<ITree> subtrees2 = getAPTEDSubtree(matchMap2, curPattern, tree2);
				
				if (subtrees1.size() == 0 || subtrees2.size() == 0) {
					String sResult = String.format("%s%n%s%n%f%n%n", srcFileName, dstFileName, 0.0);
					return new Pair<String, Double>(sResult, 0.0);
				}
				
				
				double minDistance = Double.MAX_VALUE;
				for (ITree it1 : subtrees1) {
					for (ITree it2 : subtrees2) {

						// Parse the input and transform to Node objects storing node information in MyNodeData.
						InputParser<StringNodeData> parser = new BracketStringInputParser();
						
						String trans1 = formTransaction(tc1, it1);
						String trans2 = formTransaction(tc2, it2);
						
						Node<StringNodeData> t1 = parser.fromString(trans1);
						Node<StringNodeData> t2 = parser.fromString(trans2);
						// Initialise APTED.
						APTED<StringUnitCostModel, StringNodeData> apted = new APTED<StringUnitCostModel, StringNodeData>(new StringUnitCostModel());
						// Execute APTED.
						double result = apted.computeEditDistance(t1, t2);
						
						if (minDistance > result) {
							minDistance = result;
						}
					}
				}
				
				double similarity = 0;
				if (minDistance != 0) {
					similarity = (double)1 / minDistance;
				}
				else {
					similarity = 1;
				}
				
				String sResult = String.format("%s%n%s%n%f%n%n", srcFileName, dstFileName, similarity);
//				System.out.println(srcFileName);
//				System.out.println(dstFileName);
//				System.out.println(similarity);
			
			return new Pair<String, Double>(sResult, similarity);
		}


	}
	
	
	private static String formTransaction(TreeContext tc, ITree t) {
		String result = "";
		String tag = tc.getTypeLabel(t.getType());
		
		result = result + "{" + tag;
		if (tag.equals("SimpleName")) {
			result = result + "{" + t.getLabel() + "}";
		}
		
		for (ITree ch: t.getChildren()) {
			result = result + formTransaction(tc, ch);
		}
		result = result + "}";
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
	
    private static double chawatheSimilarity(ITree src, ITree dst, int numDesc) {
        int max = Math.max(src.getDescendants().size(), dst.getDescendants().size());
        return (double) numDesc / (double) max;
    }
    
    private static double diceSimilarity(ITree src, ITree dst, int numDesc) {
        double c = (double) numDesc;
        return (2D * c) / ((double) src.getDescendants().size() + (double) dst.getDescendants().size());
    }
    
    private static double jaccardSimilarity(ITree src, ITree dst, int numDesc) {
        double num = (double) numDesc;
        double den = (double) src.getDescendants().size() + (double) dst.getDescendants().size() - num;
        return num / den;
    }
	
    private static int numberOfCommonDescendants(ITree src, ITree dst, MappingStore mappings) {
        Set<ITree> dstDescendants = new HashSet<>(dst.getDescendants());
        int common = 0;

        for (ITree t : src.getDescendants()) {
            ITree m = mappings.getDst(t);
            if (m != null && dstDescendants.contains(m))
                common++;
        }

        return common;
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
