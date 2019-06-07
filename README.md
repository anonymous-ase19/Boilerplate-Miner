MARBLE: Mining for Boilerplate Code to Identify API Usability Problems 
================
MARBLE (Mining API Repositories for Boilerplate Lessening Effort) is an automated technique for identifying instances of boilerplate API client code. MARBLE adapts existing techniques, including an API usage mining algorithm, an AST comparison algorithm, and a graph partitioning algorithm.

Further documentations for the source code and scripts to run end-to-end mining will be available soon. 
Currently, runnables for MARBLE are available for use.

Quickstart with Runnables 
------------

#### Step 0: Collecting Client Code
This step collects client code files of target APIs from large repository. It takes a number of java projects and a list of target API names as input, ```api_list.txt```:
```
javax.xml.transform
javax.swing.JFrame
...
```
After this step, you will see directories under ```Output directory``` for each target API, ```javax_xml_transform```, each of which contains symlinks to client code files.

```
$ python3 generateCorpora.py -i data/repos/ -o data/source/ -p 8 -a data/api_list.txt
```
* **-i**  &nbsp;  Repository directory containing a number of java projects
* **-o**  &nbsp;  Output directory
* **-p** &nbsp; Number of threads to use
* **-a** &nbsp; API list file


#### Step 1: Extracting API Calls
This step is to extract API calls from the set of client code files using the target API, ```javax.xml.transform```. 
```
$ java -jar APICallExtractor.jar -lf data/source/ -of data/calls/ -pn javax.xml.transform -sn 123
```
* **-lf**  &nbsp;  Client code source directory (from Step 0)
* **-of**  &nbsp;  Output directory
* **-pn**  &nbsp;  Package name
* **-sn**  &nbsp;  Sampling number (optional)

It will return ```javax_xml_transform.arff``` which contains data about 1) caller file, 2) caller package, 3) caller method, and 4) sequences of API calls, like:
```
'jenkinsci.jenkins.WebAppMain','hudson','hudson.WebAppMain.contextInitialized(javax.servlet.ServletContextEvent)','javax.xml.transform.TransformerFactory.newInstance javax.xml.transform.TransformerFactory.getName javax.xml.transform.TransformerFactory.newInstance'
'hibernate.hibernate-orm.LocalSchemaLocator','org.hibernate.boot.jaxb.internal.stax','org.hibernate.boot.jaxb.internal.stax.LocalSchemaLocator.resolveLocalSchema(java.net.URL)','javax.xml.transform.stream.StreamSource.<init>'
'deeplearning4j.deeplearning4j.Configuration','org.datavec.api.conf','org.datavec.api.conf.Configuration.writeXml(java.io.OutputStream)','javax.xml.transform.dom.DOMSource.<init> javax.xml.transform.stream.StreamResult.<init> javax.xml.transform.TransformerFactory.newInstance javax.xml.transform.TransformerFactory.newTransformer javax.xml.transform.Transformer.transform'
```

#### Step 2: Running PAM
```
$ java -jar pam.jar -f data/calls/javax_xml_transform.arff -sd data/source/javax_xml_transform/ -o data/output/javax_xml_transform/

```
* **-f**  &nbsp;  arff file from API extraction (from Step 1)
* **-sd**  &nbsp;  Source directory
* **-o**  &nbsp;  Output directory

#### Step 3: Removing Spurious Patterns
```
$ python removeSubSequences.py -i data/output/javax_xml_transform/PAM_logs.log -o data/output/javax_xml_transform/reduced_PAM_logs.log -mn 6
```
* **-i**  &nbsp;  Raw PAM log file
* **-o**  &nbsp;  Output log file after removing spurious patterns
* **-mn** &nbsp;  

#### Step 4: AST Comparision
```
$ java -jar ASTComparison.jar -f data/output/javax_xml_transform/reduced_PAM_logs.log -sd data/source/javax_xml_transform/ -o data/output/javax_xml_transform/diff/ -ps 0 -pl 6 -p 2
```
* **-f**  &nbsp;  PAM log file after removing spurious patterns
* **-sd**  &nbsp;  Client code source directory
* **-o**  &nbsp;  Output directory
* **-ps, -pl**  &nbsp;  Start and the last index of PAM patterns to set the range of AST comparision
* **p** &nbsp; Number of threads to use

#### Step 5: Graph Partitioning
```
$ python3 partitionGraph.py -i data/output/javax_xml_transform
```
* **-i**  &nbsp;  Input directory containing AST diffs

#### Step 6: Generate Viewer
```
$ python3 generateViewer.py -i data/output/javax_xml_transform -o data/output/ -s data/source/javax_xml_transform -a javax_xml_transform
```

* **-i**  &nbsp;  input directory
* **-o**  &nbsp;  output directory
* **-s**  &nbsp;  source directory
* **-a**  &nbsp;  API name

